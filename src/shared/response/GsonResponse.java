package shared.response;

import com.google.gson.Gson;
import shared.http.HTTPStatus;
import shared.http.HTTPStatusPair;

abstract class GsonResponse {
  private HTTPStatusPair status;

  GsonResponse(HTTPStatus status) {
    this.status = status.buildPair();
  }

  public String json(Gson gson) {
    return gson.toJson(this);
  }

  public HTTPStatusPair getStatus() {
    return status;
  }
}
