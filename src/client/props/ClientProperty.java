package client.props;

import shared.utils.properties.CustomPropertyType;
import shared.utils.properties.ICustomProperty;

public enum ClientProperty implements ICustomProperty {
  // Add properties here

  PUB_KEY_NAME("pub_key_name", CustomPropertyType.STRING),
  SEA_SPEC("sea_spec", CustomPropertyType.STRING),
  MAC_SPEC("mac_spec", CustomPropertyType.STRING),

  // System
  OUTPUT_FOLDER("output_folder", CustomPropertyType.STRING),
  CACHE_SIZE("cache_size", CustomPropertyType.INT),

  // Network
  PKI_ADDRESS("pki_address", CustomPropertyType.BOOL),
  PKI_PORT("pki_port", CustomPropertyType.INT),
  SERVER_ADDRESS("server_address", CustomPropertyType.STRING),
  SERVER_PORT("server_port", CustomPropertyType.INT),
  TLS_PROTOCOLS("tls_protocols", CustomPropertyType.STRING_ARRAY),
  TLS_CIPHERSUITES("tls_ciphersuites", CustomPropertyType.STRING_ARRAY),

  // Crypt
  UUID_HASH("uuid_hash", CustomPropertyType.STRING),
  KEYSTORE_LOC("keystore_location", CustomPropertyType.STRING),
  KEYSTORE_TYPE("keystore_type", CustomPropertyType.STRING),
  KEYSTORE_PASS("keystore_pass", CustomPropertyType.STRING),
  TRUSTSTORE_LOC("truststore_location", CustomPropertyType.STRING),
  TRUSTSTORE_TYPE("truststore_type", CustomPropertyType.STRING),
  TRUSTSTORE_PASS("truststore_pass", CustomPropertyType.STRING),
  BUFFER_SIZE_MB("buffer_size_megabytes", CustomPropertyType.INT);



  /////////////////////////////////////////////////

  private String val;
  private CustomPropertyType type;

  ClientProperty(String val, CustomPropertyType type) {
    this.val = val;
    this.type = type;
  }

  public String val() {
    return val;
  }

  public CustomPropertyType type() {
    return type;
  }
}
