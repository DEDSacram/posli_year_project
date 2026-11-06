package cz.bazar.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** AES-GCM sifrovani pro seller_tax_profile (bytea). Klic 32 B v base64 z TAX_ENCRYPTION_KEY. */
@Service
public class AesGcmService {
  private static final int IV_LEN = 12;
  /** Legacy vyvojovy klic — jen pro dešifrování dat zašifrovaných před nastavením TAX_ENCRYPTION_KEY. */
  private static final byte[] LEGACY_DEV_KEY =
      "dev-only-32-byte-tax-key-1234567!".getBytes(StandardCharsets.UTF_8);
  private final SecretKeySpec key;
  private final SecretKeySpec legacyKey =
      new SecretKeySpec(Arrays.copyOf(LEGACY_DEV_KEY, 32), "AES");

  private static byte[] toKeyBytes(String base64Key) {
    byte[] raw;
    try {
      raw = Base64.getDecoder().decode(base64Key.trim());
    } catch (IllegalArgumentException e) {
      raw = base64Key.getBytes(StandardCharsets.UTF_8);
    }
    return Arrays.copyOf(raw, 32);
  }

  public AesGcmService(@Value("${app.tax-key:}") String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      throw new IllegalStateException(
          "Chybí TAX_ENCRYPTION_KEY (base64 32 náhodných bajtů). Generování: openssl rand -base64 32.");
    }
    this.key = new SecretKeySpec(toKeyBytes(base64Key), "AES");
  }

  public byte[] encrypt(String plain) {
    if (plain == null) return null;
    try {
      byte[] iv = new byte[IV_LEN];
      new SecureRandom().nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[IV_LEN + ct.length];
      System.arraycopy(iv, 0, out, 0, IV_LEN);
      System.arraycopy(ct, 0, out, IV_LEN, ct.length);
      return out;
    } catch (Exception e) {
      throw new IllegalStateException("Šifrování selhalo.", e);
    }
  }

  public String decrypt(byte[] data) {
    if (data == null) return null;
    try {
      return decryptWith(data, key);
    } catch (Exception first) {
      // Data zašifrovaná starým vývojovým klíčem — zkusit legacy, pak selhat.
      try {
        return decryptWith(data, legacyKey);
      } catch (Exception e) {
        throw new IllegalStateException("Dešifrování selhalo.", first);
      }
    }
  }

  private static String decryptWith(byte[] data, SecretKeySpec k) throws Exception {
    byte[] iv = Arrays.copyOf(data, IV_LEN);
    byte[] ct = Arrays.copyOfRange(data, IV_LEN, data.length);
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, iv));
    return new String(c.doFinal(ct), StandardCharsets.UTF_8);
  }
}
