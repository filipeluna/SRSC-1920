package pki;

import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import pki.db.PKIDatabaseDriver;
import pki.props.PKIProperty;
import shared.errors.properties.InvalidValueException;
import shared.errors.properties.PropertyException;
import shared.utils.properties.CustomProperties;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.Security;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

class PKIServer {
  private static final String PROPS_PATH = "src/pki/props/pki.properties";
  private static final String PROVIDER = "BC";
  private static final String PROVIDER_TLS = "BCJSSE";

  @SuppressWarnings("InfiniteLoopStatement")
  public static void main(String[] args) {
    Security.addProvider(new BouncyCastleJsseProvider());
    System.setProperty("java.net.preferIPv4Stack", "true");

    // Get properties from file
    CustomProperties properties = null;
    // initial props
    boolean debugMode = false;

    try {
      properties = new CustomProperties(PROPS_PATH);

      debugMode = properties.getBool(PKIProperty.DEBUG);
    } catch (PropertyException e) {
      System.err.println(e.getMessage());
      System.exit(-1);
    }

    // Start main thread
    try {
      // Create thread pool for clients
      int threadPoolSize = properties.getInt(PKIProperty.THREAD_POOL_SIZE);

      if (!validateThreadCount(threadPoolSize))
        throw new InvalidValueException(PKIProperty.THREAD_POOL_SIZE.val());

      Executor executor = Executors.newFixedThreadPool(threadPoolSize);

      // Load Keystore
      String keyStorePass = properties.getString(PKIProperty.KEYSTORE_PASS);
      String keyStoreType = properties.getString(PKIProperty.KEYSTORE_TYPE);
      KeyStore keyStore = KeyStore.getInstance(keyStoreType);
      keyStore.load(new FileInputStream(properties.getString(PKIProperty.KEYSTORE_LOC)), keyStorePass.toCharArray());

      // Initiate KMF
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X509", PROVIDER_TLS);
      keyManagerFactory.init(keyStore, keyStorePass.toCharArray());

      // Create SSL Socket and initialize server
      int port = properties.getInt(PKIProperty.PORT);

      SSLContext sslContext = SSLContext.getInstance("TLS", PROVIDER_TLS);
      sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

      SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
      SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port);

      // Set enabled protocols and cipher suites
      String[] enabledProtocols = properties.getStringArr(PKIProperty.TLS_PROTOCOLS);
      String[] enabledCipherSuites = properties.getStringArr(PKIProperty.TLS_CIPHERSUITES);

      serverSocket.setEnabledProtocols(enabledProtocols);
      serverSocket.setEnabledCipherSuites(enabledCipherSuites);
      serverSocket.setNeedClientAuth(false); // Unilateral

      System.out.print("Started pki server on port " + port + "\n");

      PKIDatabaseDriver db = new PKIDatabaseDriver(properties.getString(PKIProperty.DATABASE_LOC));

      // Client serving loop
      while (true) {
        SSLSocket sslClient = (SSLSocket) serverSocket.accept();

        PKIServerResources pkiServerResources = new PKIServerResources(sslClient, properties, keyStore, db, debugMode);
        executor.execute(new Thread(pkiServerResources));
      }
    } catch (Exception e) {
      handleException(e, debugMode);
    } finally {
      System.exit(-1);
    }
  }

  /*
    Utils
  */
  private static void handleException(Exception e, boolean debugMode) {
    boolean expected = false;

    if (e instanceof PropertyException)
      expected = true;

    if (expected) {
      System.err.println(e.getMessage());
    } else {
      System.err.println("CRITICAL ERROR.");
    }

    if (debugMode)
      e.printStackTrace();
  }

  private static boolean validateThreadCount(int threadCount) {
    int totalThreads = Runtime.getRuntime().availableProcessors();

    // Keep threads between 0 < threads < CPUTOTAL
    return totalThreads > threadCount && threadCount > 0;
  }
}
