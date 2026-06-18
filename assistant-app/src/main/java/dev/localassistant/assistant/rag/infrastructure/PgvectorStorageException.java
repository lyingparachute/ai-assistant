package dev.localassistant.assistant.rag.infrastructure;

class PgvectorStorageException extends RuntimeException {

    PgvectorStorageException(String message) {
        super(message);
    }

    PgvectorStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
