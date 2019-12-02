package shared.utils.crypto;

import shared.utils.CryptUtil;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.DHGenParameterSpec;
import javax.crypto.spec.DHParameterSpec;
import java.math.BigInteger;
import java.security.*;

public class DHHelper {
  private MessageDigest hash;
  private KeyPairGenerator keyPairGenerator;
  private KeyAgreement keyAgreement;
  private AlgorithmParameterGenerator algParamsGenerator;

  public DHHelper(String dhAlg, String hashAlg, int keySize) throws GeneralSecurityException {
    keyPairGenerator = KeyPairGenerator.getInstance(dhAlg, CryptUtil.PROVIDER);
    keyAgreement = KeyAgreement.getInstance(dhAlg, CryptUtil.PROVIDER);
    hash = MessageDigest.getInstance(hashAlg, CryptUtil.PROVIDER);
    algParamsGenerator = AlgorithmParameterGenerator.getInstance(dhAlg, CryptUtil.PROVIDER);

    algParamsGenerator.init(keySize);
  }

  public KeyPair genKeyPair(DHParameterSpec spec) throws GeneralSecurityException {
    keyPairGenerator.initialize(spec);

    return keyPairGenerator.generateKeyPair();
  }

  public byte[] genSharedKey(PrivateKey aKey, PublicKey bKey) throws GeneralSecurityException {
    keyAgreement.init(aKey);
    keyAgreement.doPhase(bKey, true); // true - last phase

    return hash.digest(keyAgreement.generateSecret());
  }

  public static BigInteger generatePrime(int size) {
    return BigInteger.probablePrime(size, new SecureRandom());
  }


  public DHParameterSpec genParams() throws GeneralSecurityException {
    AlgorithmParameters algParams = algParamsGenerator.generateParameters();

    return algParams.getParameterSpec(DHParameterSpec.class);
  }
}
