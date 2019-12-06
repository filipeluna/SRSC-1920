package shared.utils.crypto;

import shared.utils.CryptUtil;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;

public final class HashHelper {
  private static final String PROVIDER = CryptUtil.PROVIDER;

  private MessageDigest messageDigest;
  private B64Helper b64Helper;

  public HashHelper(String algorithm, B64Helper b64Helper) throws GeneralSecurityException {
    this.messageDigest = MessageDigest.getInstance(algorithm, PROVIDER);
  }

  public byte[] hash(byte[] data) {
    return messageDigest.digest(data);
  }

  public String hashAndEncode(byte[] data) {
    return b64Helper.encode(hash(data));
  }

  public boolean verifyHash(byte[] decodedData, byte[] decodedHash) {
    byte[] reHash = hash(decodedData);

    return MessageDigest.isEqual(reHash, decodedHash);
  }

  public boolean verifyEncodedHash(String encodedData, byte[] decodedHash) {
    return verifyHash(b64Helper.decode(encodedData), decodedHash);
  }

  public boolean verifyEncodedHash(String encodedData, String encodedHash) {
    return verifyHash(b64Helper.decode(encodedData), b64Helper.decode(encodedHash));
  }

  public int getHashSize() {
    return messageDigest.getDigestLength();
  }
}
