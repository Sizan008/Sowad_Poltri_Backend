package com.sowad.nourish.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Versioned authenticated encryption, with a separate derived blind-index key. */
public final class AccountCipher {
  public static final String PREFIX = "enc:v1:";
  private final byte[] encryptionKey;
  private final byte[] lookupKey;
  private final SecureRandom random = new SecureRandom();

  public AccountCipher(String base64Key) {
    try {
      byte[] master = Base64.getDecoder().decode(base64Key == null ? "" : base64Key);
      if (master.length != 32) throw new IllegalArgumentException();
      encryptionKey = mac(master, "nourish/account-encryption/v1");
      lookupKey = mac(master, "nourish/account-lookup/v1");
      Arrays.fill(master, (byte) 0);
    } catch (Exception e) {
      throw new IllegalStateException("ACCOUNT_DATA_KEY must be Base64 encoding of exactly 32 random bytes.");
    }
  }

  private static byte[] mac(byte[] key, String value) throws GeneralSecurityException {
    Mac m = Mac.getInstance("HmacSHA256");
    m.init(new SecretKeySpec(key, "HmacSHA256"));
    return m.doFinal(value.getBytes(StandardCharsets.UTF_8));
  }

  public String lookup(String field, String value) {
    if (value == null) return null;
    try { return HexFormat.of().formatHex(mac(lookupKey, field + "\0" + value)); }
    catch (GeneralSecurityException e) { throw new IllegalStateException("Account lookup failed."); }
  }

  public String encrypt(String value, String context) {
    if (value == null) return null;
    try {
      byte[] nonce = new byte[12]; random.nextBytes(nonce);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
      c.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      byte[] ciphertext = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
      byte[] result = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
      System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
      return PREFIX + Base64.getEncoder().encodeToString(result);
    } catch (GeneralSecurityException e) { throw new IllegalStateException("Account encryption failed."); }
  }

  public String decrypt(String value, String context) {
    if (value == null) return null;
    try {
      if (!value.startsWith(PREFIX)) throw new IllegalArgumentException();
      byte[] bytes = Base64.getDecoder().decode(value.substring(PREFIX.length()));
      if (bytes.length < 28) throw new IllegalArgumentException();
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
          new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
      c.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      return new String(c.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
    } catch (Exception e) {
      // Never include a key, plaintext, ciphertext or provider exception in the API error.
      throw new IllegalStateException("Account decryption failed; verify the encryption key and stored data.");
    }
  }
}
