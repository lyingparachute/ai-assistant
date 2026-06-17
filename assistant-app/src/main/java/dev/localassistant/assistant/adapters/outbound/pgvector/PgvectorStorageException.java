package dev.localassistant.assistant.adapters.outbound.pgvector;

class PgvectorStorageException extends RuntimeException {

    PgvectorStorageException(String message) {
        super(message);
    }

    PgvectorStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
