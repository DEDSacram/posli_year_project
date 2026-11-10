package cz.bazar.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/** Šifrování daňového profilu: roundtrip, detekce manipulace a chybějící klíč. */
class AesGcmServiceTest {

  private static AesGcmService service() {
    byte[] key = new byte[32];
    for (int i = 0; i < 32; i++) key[i] = (byte) i;
    return new AesGcmService(Base64.getEncoder().encodeToString(key));
  }

  @Test
  void encryptThenDecryptRoundTrip() {
    AesGcmService s = service();
    byte[] enc = s.encrypt("CZ65 0800 0000 1920 0014 5399");
    assertThat(enc).isNotEqualTo("CZ65 0800 0000 1920 0014 5399".getBytes(StandardCharsets.UTF_8));
    assertThat(s.decrypt(enc)).isEqualTo("CZ65 0800 0000 1920 0014 5399");
  }

  @Test
  void nullStaysNull() {
    AesGcmService s = service();
    assertThat(s.encrypt(null)).isNull();
    assertThat(s.decrypt(null)).isNull();
  }

  @Test
  void tamperedCiphertextFailsDecrypt() {
    AesGcmService s = service();
    byte[] enc = s.encrypt("tajne");
    enc[enc.length - 1] ^= 0x01; // změna autentizačního tagu
    assertThatThrownBy(() -> s.decrypt(enc))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void wrongKeyCannotDecrypt() {
    byte[] key = new byte[32];
    byte[] other = new byte[32];
    for (int i = 0; i < 32; i++) { key[i] = (byte) i; other[i] = (byte) (i + 1); }
    byte[] enc = new AesGcmService(Base64.getEncoder().encodeToString(key)).encrypt("tajne");
    AesGcmService wrong = new AesGcmService(Base64.getEncoder().encodeToString(other));
    assertThatThrownBy(() -> wrong.decrypt(enc)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void missingKeyFailsFast() {
    assertThatThrownBy(() -> new AesGcmService(""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TAX_ENCRYPTION_KEY");
  }
}