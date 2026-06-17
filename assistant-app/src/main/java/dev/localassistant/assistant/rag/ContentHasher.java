package dev.localassistant.assistant.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ContentHasher {

    private static final HexFormat HEX = HexFormat.of();

    private ContentHasher() {
    }

    public static String sha256Hex(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            throw new IllegalArgumentException("normalizedText must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedText.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
