package dev.localassistant.assistant.shared;

import java.util.Objects;

public record SourceUnavailability(String sourceLabel, String message, String hint) {

    public SourceUnavailability {
        Objects.requireNonNull(sourceLabel, "sourceLabel");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(hint, "hint");
        if (sourceLabel.isBlank()) {
            throw new IllegalArgumentException("sourceLabel must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (hint.isBlank()) {
            throw new IllegalArgumentException("hint must not be blank");
        }
    }
}
