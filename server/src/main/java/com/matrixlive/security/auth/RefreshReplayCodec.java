package com.matrixlive.security.auth;

import com.matrixlive.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.stereotype.Component;

@Component
public class RefreshReplayCodec {
  private final SecretKeySpec key;

  public RefreshReplayCodec(JwtProperties properties) throws NoSuchAlgorithmException {
    key =
        new SecretKeySpec(
            MessageDigest.getInstance("SHA-256")
                .digest(Base64.getDecoder().decode(properties.getSecret())),
            "AES");
  }

  public String encrypt(String raw) {
    try {
      byte[] iv = new byte[12];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] encrypted = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));
      byte[] result = Arrays.copyOf(iv, iv.length + encrypted.length);
      System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
      return Base64.getEncoder().encodeToString(result);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot encrypt refresh replay", e);
    }
  }

  public String decrypt(String encrypted) {
    try {
      byte[] value = Base64.getDecoder().decode(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(value, 12)));
      return new String(
          cipher.doFinal(Arrays.copyOfRange(value, 12, value.length)), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot decrypt refresh replay", e);
    }
  }
}
