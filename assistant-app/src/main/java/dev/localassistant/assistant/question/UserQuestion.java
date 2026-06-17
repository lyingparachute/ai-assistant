package dev.localassistant.assistant.question;

import java.util.Objects;

public record UserQuestion(String text) {

    public UserQuestion {
        Objects.requireNonNull(text, "text");
        text = text.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }

    public static UserQuestion of(String rawText) {
        Objects.requireNonNull(rawText, "rawText");
        return new UserQuestion(rawText);
    }
}
