package org.star5025.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Base64;

@Service
public class PasswordService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String encode(String password) {
        if (password == null || password.isBlank() || password.length() < 5
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少5个字符且不能超过72字节");
        }
        return encoder.encode(password);
    }

    // Historical passwords may violate the new-password policy. Never truncate them during migration.
    public String encodeLegacy(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Legacy password is required");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "{bcrypt-sha256}" + encoder.encode(prehash(password));
        }
        return encoder.encode(password);
    }

    private String prehash(String password) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public boolean isLegacyMd5(String encoded) {
        return encoded != null && encoded.matches("[a-fA-F0-9]{32}");
    }

    public boolean matches(String password, String encoded) {
        if (password == null || password.isEmpty() || encoded == null) {
            return false;
        }
        if (encoded.startsWith("{bcrypt-sha256}")) {
            String hash = encoded.substring("{bcrypt-sha256}".length());
            return hash.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")
                    && encoder.matches(prehash(password), hash);
        }
        if (encoded.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")) {
            return password.getBytes(StandardCharsets.UTF_8).length <= 72 && encoder.matches(password, encoded);
        }
        if (!isLegacyMd5(encoded)) {
            return false;
        }
        try {
            // A fresh digest per invocation avoids the old shared MessageDigest race.
            byte[] actual = MessageDigest.getInstance("MD5").digest(password.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(actual, HexFormat.of().parseHex(encoded));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 verification unavailable", e);
        }
    }
}
