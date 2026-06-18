package dev.localassistant.assistant.shared;

import java.util.Objects;

public sealed interface ToolExecutionResult<T> {

    default SourceUnavailability asUnavailability(final String fallbackLabel) {
        return switch (this) {
            case final Success<T> ignored -> throw new IllegalStateException("asUnavailability requires a failed result");
            case ToolError<T>(final var error, final var hint) -> new SourceUnavailability(fallbackLabel, error, hint);
            case SourceUnavailable<T>(final var unavailability) -> unavailability;
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

        public SourceUnavailable(final String sourceLabel, final String message, final String hint) {
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
