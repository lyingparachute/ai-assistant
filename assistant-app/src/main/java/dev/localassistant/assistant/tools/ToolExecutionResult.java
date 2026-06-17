package dev.localassistant.assistant.tools;

import java.util.Objects;

public sealed interface ToolExecutionResult<T> {

    record Success<T>(T value) implements ToolExecutionResult<T> {
        public Success {
            Objects.requireNonNull(value, "value");
        }
    }

    record ToolError<T>(String error, String hint) implements ToolExecutionResult<T> {
        public ToolError {
            Objects.requireNonNull(error, "error");
            Objects.requireNonNull(hint, "hint");
            if (error.isBlank()) {
                throw new IllegalArgumentException("error must not be blank");
            }
        }
    }

    record SourceUnavailable<T>(String sourceLabel, String message, String hint) implements ToolExecutionResult<T> {
        public SourceUnavailable {
            Objects.requireNonNull(sourceLabel, "sourceLabel");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(hint, "hint");
            if (sourceLabel.isBlank()) {
                throw new IllegalArgumentException("sourceLabel must not be blank");
            }
        }
    }
}
