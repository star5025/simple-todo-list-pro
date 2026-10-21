package org.star5025.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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

    public boolean isLegacyMd5(String encoded) {
        return encoded != null && encoded.matches("[a-fA-F0-9]{32}");
    }

    public boolean matches(String password, String encoded) {
        if (password == null || password.isEmpty() || encoded == null
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        if (encoded.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")) {
            return encoder.matches(password, encoded);
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
