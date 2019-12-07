package server;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import server.db.wrapper.Message;
import server.db.wrapper.Receipt;
import server.response.*;
import server.db.ServerParameterMap;
import server.db.wrapper.User;
import shared.Pair;
import shared.ServerRequest;
import shared.errors.IHTTPStatusException;
import shared.errors.db.*;
import shared.errors.request.*;
import shared.http.HTTPStatus;
import shared.response.ErrorResponse;
import shared.response.GsonResponse;
import shared.utils.GsonUtils;
import shared.utils.SafeInputStreamReader;

import javax.net.ssl.SSLSocket;
import java.security.cert.X509Certificate;
import java.security.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;

final class ServerResources implements Runnable {
  private final SSLSocket client;
  private JsonReader input;
  private OutputStream output;

  private final ServerProperties props;
  private X509Certificate clientCert;

  ServerResources(SSLSocket client, ServerProperties props) {
    this.client = client;
    this.props = props;

    try {
      input = new JsonReader(new SafeInputStreamReader(client.getInputStream(), props.getBufferSizeInMB()));
      output = client.getOutputStream();
    } catch (Exception e) {
      handleException(e);
    }
  }

  public void run() {
    try {
      clientCert = props.AEA.getCertFromSession(client);

      // Verify client certificate validity in PKI (like OCSP)
      if (props.PKI_ENABLED) {
        SSLSocket pkiSocket = props.PKI_COMMS_MGR.getSocket();

        // Check certificate validity in PKI
        props.PKI_COMMS_MGR.checkClientCertificateRevoked(clientCert, pkiSocket);
      }

      // Serve client request
      JsonObject parsedRequest = parseRequest(input);

      handleRequest(parsedRequest);

      client.close();
    } catch (Exception e) {
      handleException(e);
      Thread.currentThread().interrupt();
    }
  }

  private void handleRequest(JsonObject requestData) throws RequestException, IOException, CriticalDatabaseException {
    try {
      String requestName = GsonUtils.getString(requestData, "type");
      ServerRequest request = ServerRequest.fromString(requestName);
      String nonce = null;

      // Check route is valid
      if (request == null)
        throw new InvalidRouteException();

      // Get nonce if supposed to for the requested route
      if (request.needsNonce())
        nonce = GsonUtils.getString(requestData, "nonce");

      // Log client request without any specific info.
      // Certificates emitted by CA should have unique serial numbers
      // This way, we can identify the principal if a DOS or other similar attack occurs
      props.LOGGER.log(Level.FINE, "Request: " + requestName + " made by " + clientCert.getSerialNumber() + ".");

      switch (request) {
        case CREATE:
          insertUser(requestData, nonce);
          break;
        case LIST:
          listUsers(requestData, nonce);
          break;
        case NEW:
          listNewMessages(requestData, nonce);
          break;
        case ALL:
          listMessages(requestData, nonce);
          break;
        case SEND:
          insertMessage(requestData, nonce);
          break;
        case RECEIVE:
          getMessage(requestData, nonce);
          break;
        case RECEIPT:
          insertReceipt(requestData);
          break;
        case STATUS:
          getReceipts(requestData, nonce);
          break;
        case PARAMS:
          params(nonce);
          break;
      }
    } catch (ClassCastException | IllegalStateException e) {
      throw new InvalidRouteException();
    }
  }

  // Create user message box
  private synchronized void insertUser(JsonObject requestData, String nonce) throws RequestException, IOException, CriticalDatabaseException {
    // Get public key and certificate from user
    PublicKey publicKey = clientCert.getPublicKey();
    String publicKeyEncoded = props.B64.encode(publicKey.getEncoded());

    // Get user intended uuid and message verification nonce
    String uuid = GsonUtils.getString(requestData, "uuid");

    // Get extra fields
    String dhSeaPubKey = GsonUtils.getString(requestData, "dhSeaPubKey");
    String dhMacPubKey = GsonUtils.getString(requestData, "dhMacPubKey");
    String seaSpec = GsonUtils.getString(requestData, "seaSpec");
    String macSpec = GsonUtils.getString(requestData, "macSpec");

    // Get extra fields signature
    String secDataSignature = GsonUtils.getString(requestData, "secDataSignature");

    User user = new User(
        uuid,
        publicKeyEncoded,
        dhSeaPubKey,
        dhMacPubKey,
        seaSpec,
        macSpec,
        secDataSignature
    );

    // Insert user and send response
    try {
      int userId = props.DB.insertUser(user);

      send(new CreateUserResponse(nonce, userId));
    } catch (DuplicateEntryException e) {
      throw new CustomRequestException("User ID already registered.", HTTPStatus.BAD_REQUEST);
    }
  }

  // List users details
  private void listUsers(JsonObject requestData, String nonce) throws RequestException, IOException, CriticalDatabaseException {
    // Get intended user id or none if supposed to get all users
    int userId;

    // User ID will be null if supposed to get all users
    try {
      userId = GsonUtils.getInt(requestData, "userId");
    } catch (MissingValueException e) {
      userId = -1;
    }

    ArrayList<User> users = new ArrayList<>();


    // Detect if supposed to get 1 or multiple users
    try {
      if (userId > 0)
        users.add(props.DB.getUser(userId));
      else
        users = props.DB.getAllUsers();
    } catch (EntryNotFoundException e) {
      throw new CustomRequestException("User id not found", HTTPStatus.NOT_FOUND);
    }

    // Parse user list to json and send
    String usersJSON = props.GSON.toJson(users);

    send(new ListUsersResponse(nonce, usersJSON));
  }

  // List new messages
  private void listNewMessages(JsonObject requestData, String nonce) throws RequestException, IOException, CriticalDatabaseException {
    // Get intended user id or none if supposed to get all users
    int userId = GsonUtils.getInt(requestData, "userId");

    // Get unread messages and create response object
    ArrayList<Integer> newMessageIds = props.DB.getUnreadMessages(userId);
    send(new ListNewMessagesResponse(nonce, newMessageIds));
  }

  // List all messages
  private void listMessages(JsonObject requestData, String nonce) throws RequestException, IOException, CriticalDatabaseException {
    // Get intended user id messages
    int userId = GsonUtils.getInt(requestData, "userId");

    // Get all messages, split between received/sent and create response object
    Pair<ArrayList<String>, ArrayList<Integer>> messages = props.DB.getAllMessages(userId);

    ArrayList<String> receivedMessageIds = messages.getA();
    ArrayList<Integer> sentMessagesIds = messages.getB();

    send(new ListMessagesResponse(nonce, receivedMessageIds, sentMessagesIds));
  }

  // Is Revoked
  private synchronized void insertMessage(JsonObject requestData, String nonce) throws RequestException, IOException, CriticalDatabaseException {
    // Get sender and receiver ids
    int senderId = GsonUtils.getInt(requestData, "senderId");
    int receiverId = GsonUtils.getInt(requestData, "receiverId");

    // Get message (encrypted and encoded) parts and respective mac hash of them
    String text = GsonUtils.getString(requestData, "text");

    byte[] attachments = new byte[0];
    String attachmentData = "";

    // Check if attachment was sent
    try {
      attachmentData = GsonUtils.getString(requestData, "attachmentData");
      attachments = props.B64.decode(GsonUtils.getString(requestData, "attachments"));
    } catch (MissingValueException e) {
      // Message has no attachments
    }

    String cipherIV = "";
    //Security part
    try {
      cipherIV = GsonUtils.getString(requestData, "cipherIV");
    } catch (MissingValueException e) {
      // Message cipher does not use IV
    }

    String senderSignature = GsonUtils.getString(requestData, "senderSignature");

    // Create and insert message
    Message message = new Message(
        senderId,
        receiverId,
        text,
        attachmentData,
        attachments,
        cipherIV,
        senderSignature
    );

    // Try to insert message in db
    try {
      int insertedMessageId = props.DB.insertMessage(message);
      send(new SendMessageResponse(nonce, insertedMessageId));
    } catch (DatabaseException e) {
      throw new CustomRequestException("User id not found", HTTPStatus.NOT_FOUND);
    }
  }

  private void getMessage(JsonObject requestData, String nonce) throws RequestException, IOException, CriticalDatabaseException {
    // Get intended message id
    int messageId = GsonUtils.getInt(requestData, "messageId");

    // Get specific message and create response object
    try {
      Message message = props.DB.getMessage(messageId);
      send(new ReceiveMessageResponse(nonce, message));
    } catch (EntryNotFoundException e) {
      throw new CustomRequestException("Message id not found", HTTPStatus.NOT_FOUND);
    }
  }


  private synchronized void insertReceipt(JsonObject requestData) throws RequestException, CriticalDatabaseException {
    // Get read message id
    int messageId = GsonUtils.getInt(requestData, "messageId");

    // Get receiver signature -> message contents signature
    String receiverSignature = GsonUtils.getString(requestData, "receiverSignature");

    // Get signature date
    String date = GsonUtils.getString(requestData, "date");

    // Insert message receipt
    try {
      props.DB.insertReceipt(new Receipt(messageId, date, receiverSignature));

      // Set message as read
      props.DB.setMessageAsRead(messageId);
    } catch (FailedToInsertException | EntryNotFoundException e) {
      throw new CustomRequestException("Message id not found", HTTPStatus.NOT_FOUND);
    }
  }

  private void getReceipts(JsonObject requestData, String nonce) throws RequestException, CriticalDatabaseException, IOException {
    // Get intended message id
    int messageId = GsonUtils.getInt(requestData, "messageId");

    // TODO Why send the message here? We have a dedicated route....
    try {
      // Get the message and its respective receipts
      ArrayList<Receipt> receipts = props.DB.getReceipts(messageId);
      Message message = props.DB.getMessage(messageId);

      // Create response and send
      send(new MessageReceiptsResponse(nonce, message, receipts));
    } catch (EntryNotFoundException e) {
      throw new CustomRequestException("Message id not found", HTTPStatus.NOT_FOUND);
    }
  }

  // Get all server params
  private void params(String nonce) throws CriticalDatabaseException, IOException {
    // Get params and parse to json object
    ServerParameterMap params = props.DB.getAllParameters();
    String paramsJSON = props.GSON.toJson(params);

    send(new ParametersResponse(nonce, paramsJSON, paramsJSON));
  }

  /*
    UTILS
  */
  private JsonObject parseRequest(JsonReader reader) throws InvalidFormatException {
    JsonElement data = new JsonParser().parse(reader);

    if (!data.isJsonObject())
      throw new InvalidFormatException();

    return data.getAsJsonObject();
  }

  private void handleException(Exception exception) {
    ErrorResponse response;

    if (exception instanceof IHTTPStatusException) {
      HTTPStatus status = ((IHTTPStatusException) exception).status();
      response = status.buildErrorResponse(exception.getMessage());

      props.LOGGER.log(Level.WARNING, exception.getMessage());
    } else {
      System.err.println("Client disconnected due to critical error: " + exception.getMessage());

      if (props.DEBUG_MODE)
        exception.printStackTrace();

      response = HTTPStatus.INTERNAL_SERVER_ERROR.buildErrorResponse();
      props.LOGGER.log(Level.SEVERE, exception.getMessage());
    }

    try {
      send(response);
    } catch (IOException e) {
      System.err.println("Failed to send error response to client");

      if (props.DEBUG_MODE)
        e.printStackTrace();

      props.LOGGER.log(Level.SEVERE, exception.getMessage());
    }
  }

  private void send(GsonResponse response) throws IOException {
    output.write(response.json(props.GSON).getBytes(StandardCharsets.UTF_8));
  }

  private String getCurrentDate() {
    DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    return df.format(new Date());
  }
}