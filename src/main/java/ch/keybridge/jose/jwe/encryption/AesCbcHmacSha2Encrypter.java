package ch.keybridge.jose.jwe.encryption;

import ch.keybridge.jose.util.SecureRandomUtility;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * https://tools.ietf.org/html/rfc7516#appendix-B
 * https://tools.ietf.org/html/draft-mcgrew-aead-aes-cbc-hmac-sha2-05#appendix-B
 */
public class AesCbcHmacSha2Encrypter implements Encrypter {

  final static int IV_BYTE_LENGTH = 128 / 8;
  private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
  private static final String SECRET_KEY_ALGORITHM = "AES";
  private final Configuration configuration;

  public AesCbcHmacSha2Encrypter(Configuration configuration) {
    this.configuration = configuration;
  }

  /**
   * @param l
   * @return
   */
  private static byte[] getUnsignedLongBytes(long l) {
    byte[] result = new byte[8];
    for (int i = 7; i >= 0; i--) {
      result[i] = (byte) (l & 0xFF);
      l >>= 8;
    }
    return result;
  }

  private static byte[] concatenate(byte[] aad, byte[] iv, byte[] ciphertext, byte[] a) {
    byte[] output = new byte[aad.length + iv.length + ciphertext.length + a.length];
    int idx = 0;
    System.arraycopy(aad, 0, output, idx, aad.length);
    idx += aad.length;
    System.arraycopy(iv, 0, output, idx, iv.length);
    idx += iv.length;
    System.arraycopy(ciphertext, 0, output, idx, ciphertext.length);
    idx += ciphertext.length;
    System.arraycopy(a, 0, output, idx, a.length);
    return output;
  }

  @Override
  public Key generateKey() throws GeneralSecurityException {
    return new SecretKeySpec(SecureRandomUtility.generateBytes(configuration.INPUT_KEY_LENGTH), SECRET_KEY_ALGORITHM);
  }

  @Override
  public EncryptionResult encrypt(byte[] payload, byte[] iv, byte[] aad, Key key) throws GeneralSecurityException {
    if (iv == null) {
      iv = SecureRandomUtility.generateBytes(IV_BYTE_LENGTH);
    }
    if (aad == null) {
      aad = new byte[0];
    }
    validateInputs(key, aad, iv);

    Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
    cipher.init(Cipher.ENCRYPT_MODE, generateEncryptionKey(key), new IvParameterSpec(iv));
    final byte[] ciphertext = cipher.doFinal(payload);

    return new EncryptionResult(iv, aad, ciphertext, calculateAuthenticationTag(ciphertext, aad, iv, key));
  }

  @Override
  public byte[] decrypt(byte[] ciphertext, byte[] iv, byte[] aad, byte[] authTag, Key key) throws
    GeneralSecurityException {
    if (aad == null) {
      aad = new byte[0];
    }
    validateInputs(key, aad, iv);
    if (!Arrays.equals(authTag, calculateAuthenticationTag(ciphertext, aad, iv, key))) {
      return null;
    }

    Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
    cipher.init(Cipher.DECRYPT_MODE, generateEncryptionKey(key), new IvParameterSpec(iv));
    try {
      return cipher.doFinal(ciphertext);
    } catch (BadPaddingException e) {
      return null;
    }
  }

  private byte[] calculateAuthenticationTag(byte[] ciphertext, byte[] aad, byte[] iv, Key key) throws
    GeneralSecurityException {
    final byte[] al = getUnsignedLongBytes(aad.length * 8);
    final byte[] macInput = concatenate(aad, iv, ciphertext, al);
    Mac mac = Mac.getInstance(configuration.JCE_MAC_ALG);
    mac.init(generateMacKey(key));
    byte[] macValue = mac.doFinal(macInput);
    return Arrays.copyOf(macValue, configuration.T_LEN);
  }

  private void validateInputs(Key key, byte[] aad, byte[] iv) {
    if (key.getEncoded().length != configuration.INPUT_KEY_LENGTH) {
      throw new IllegalArgumentException("Key must be " + configuration.INPUT_KEY_LENGTH + " bytes in length. Key "
        + "length:" + key.getEncoded().length);
    }
    if (!key.getAlgorithm().equals(SECRET_KEY_ALGORITHM)) {
      throw new IllegalArgumentException("SecretKey must be an AES key");
    }
    if (iv == null || iv.length != IV_BYTE_LENGTH) {
      throw new IllegalArgumentException("Initialisation vector must be " + IV_BYTE_LENGTH + " bytes long. "
        + "Provided IV: " + (iv == null ? null : (iv.length + " bytes. ")));
    }
    if (aad == null) {
      throw new IllegalArgumentException("Additional authenticated data must not be null");
    }
  }

  private SecretKey generateMacKey(Key key) {
    return new SecretKeySpec(Arrays.copyOf(key.getEncoded(), configuration.MAC_KEY_LEN), SECRET_KEY_ALGORITHM);
  }

  private SecretKey generateEncryptionKey(Key key) {
    return new SecretKeySpec(Arrays.copyOfRange(key.getEncoded(), configuration.MAC_KEY_LEN, configuration.INPUT_KEY_LENGTH),
                             SECRET_KEY_ALGORITHM);
  }

  public enum Configuration {
    AES_128_CBC_HMAC_SHA_256(32, 16, 16, "SHA-256", "HmacSHA256", 16),
    AES_192_CBC_HMAC_SHA_384(48, 24, 24, "SHA-384", "HmacSHA384", 24),
    AES_256_CBC_HMAC_SHA_512(64, 32, 32, "SHA-512", "HmacSHA512", 32);

    public final int INPUT_KEY_LENGTH; // number of octets in input key
    public final int ENC_KEY_LEN; // number of octets in encryption key
    public final int MAC_KEY_LEN; // number of octets in MAC key
    public final String MAC_ALG; // JOSE MAC algorithm name
    public final String JCE_MAC_ALG; // Java (JCE) MAC algorithm name
    public final int T_LEN; // Authentication tag length in bytes

    /**
     * Create immutable configuration containing parameters for the
     * AES-CBC-HMAC-SHA2 encryption scheme.
     *
     * @param INPUT_KEY_LENGTH number of octets in input key
     * @param ENC_KEY_LEN      number of octets in encryption key
     * @param MAC_KEY_LEN      number of octets in MAC key
     * @param MAC_ALG          JOSE MAC algorithm name
     * @param jce_mac_alg      Java (JCE) MAC algorithm name
     * @param t_LEN            Authentication tag length in bytes
     */
    Configuration(int INPUT_KEY_LENGTH, int ENC_KEY_LEN, int MAC_KEY_LEN, String MAC_ALG, String jce_mac_alg, int t_LEN) {
      this.INPUT_KEY_LENGTH = INPUT_KEY_LENGTH;
      this.ENC_KEY_LEN = ENC_KEY_LEN;
      this.MAC_KEY_LEN = MAC_KEY_LEN;
      this.MAC_ALG = MAC_ALG;
      JCE_MAC_ALG = jce_mac_alg;
      T_LEN = t_LEN;
    }
  }
}
