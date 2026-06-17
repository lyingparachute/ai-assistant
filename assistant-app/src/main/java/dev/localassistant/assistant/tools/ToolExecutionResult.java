package dev.localassistant.assistant.tools;

import java.util.Objects;

public sealed interface ToolExecutionResult<T> {

    default SourceUnavailability asUnavailability(String fallbackLabel) {
        return switch (this) {
            case Success<T> ignored ->
                    throw new IllegalStateException("asUnavailability requires a failed result");
            case ToolError<T> toolError ->
                    new SourceUnavailability(fallbackLabel, toolError.error(), toolError.hint());
            case SourceUnavailable<T> unavailable -> unavailable.unavailability();
        };
    }

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

    record SourceUnavailable<T>(SourceUnavailability unavailability)
            implements ToolExecutionResult<T> {
        public SourceUnavailable {
            Objects.requireNonNull(unavailability, "unavailability");
        }

        public SourceUnavailable(String sourceLabel, String message, String hint) {
            this(new SourceUnavailability(sourceLabel, message, hint));
        }

        public String sourceLabel() {
            return unavailability.sourceLabel();
        }

        public String message() {
            return unavailability.message();
        }

        public String hint() {
            return unavailability.hint();
        }
    }
}
