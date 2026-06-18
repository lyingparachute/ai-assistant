package dev.localassistant.assistant.rag.infrastructure;

class PgvectorStorageException extends RuntimeException {

    PgvectorStorageException(final String message) {
        super(message);
    }

    PgvectorStorageException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
