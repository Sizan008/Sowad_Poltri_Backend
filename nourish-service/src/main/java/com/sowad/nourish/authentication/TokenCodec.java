package com.sowad.nourish.authentication;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class TokenCodec {
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
