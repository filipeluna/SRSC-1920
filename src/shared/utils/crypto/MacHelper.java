package shared.utils.crypto;

import shared.utils.crypto.util.KeySizeFinder;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;

public final class MacHelper {
  private static final String PROVIDER = "BC";

  private Mac mac;
  private KeyGenerator keyGen;

  public MacHelper(String algorithm) throws NoSuchProviderException, NoSuchAlgorithmException {
    mac = Mac.getInstance(algorithm, PROVIDER);
    keyGen = KeyGenerator.getInstance(algorithm, PROVIDER);
  }

  public boolean verifyMacHash(byte[] data, byte[] hash, Key key) throws InvalidKeyException {
    byte[] reHashedBytes = macHash(data, key);

    return MessageDigest.isEqual(reHashedBytes, hash);
  }

  public byte[] macHash(byte[] data, Key key) throws InvalidKeyException {
    mac.init(key);

    return mac.doFinal(data);
  }

  public int getMacSize() {
    return mac.getMacLength();
  }

  public SecretKey getKeyFromBytes(byte[] keyBytes) {
    return new SecretKeySpec(keyBytes, mac.getAlgorithm());
  }

  public SecretKey generateKey() {
    keyGen.init(mac.getMacLength(), new SecureRandom());

    return keyGen.generateKey();
  }

  public int getMaxKeySize() throws NoSuchAlgorithmException {
    return KeySizeFinder.findMaxMac(mac.getAlgorithm());
  }
}
