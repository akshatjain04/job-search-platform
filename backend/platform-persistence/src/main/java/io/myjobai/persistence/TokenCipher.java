package io.myjobai.persistence;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

public final class TokenCipher {
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public TokenCipher(String base64Key) {
    try {
      byte[] raw = Base64.getDecoder().decode(base64Key);
      if (raw.length != 32) throw new IllegalArgumentException();
      key = new SecretKeySpec(raw, "AES");
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
          "ENCRYPTION_MASTER_KEY must be base64 encoding exactly 32 random bytes");
    }
  }

  public String encrypt(String plaintext, String context) {
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      return "v1."
          + Base64.getEncoder().encodeToString(iv)
          + "."
          + Base64.getEncoder()
              .encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Token encryption failed");
    }
  }

  public String decrypt(String encrypted, String context) {
    try {
      var parts = encrypted.split("\\.");
      if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      return new String(
          cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Encrypted token could not be authenticated; check key and record context");
    }
  }
}
