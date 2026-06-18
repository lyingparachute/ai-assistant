package dev.localassistant.assistant.rag.infrastructure;

class ProductPageFetchException extends RuntimeException {

    ProductPageFetchException(final String message) {
        super(message);
    }
}
