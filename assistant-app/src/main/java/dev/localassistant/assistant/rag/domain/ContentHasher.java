package dev.localassistant.assistant.rag.domain;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@UtilityClass
class ContentHasher {

    private static final HexFormat HEX = HexFormat.of();

    public static String sha256Hex(final String normalizedText) {
        if (StringUtils.isBlank(normalizedText)) {
            throw new IllegalArgumentException("normalizedText must not be blank");
        }
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            final var hash = digest.digest(normalizedText.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
