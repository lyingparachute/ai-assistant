package dev.localassistant.assistant.rag.domain.port.inbound;

import dev.localassistant.assistant.rag.domain.RagIngestionResult;

public interface IngestRag {

    RagIngestionResult execute(Command command);

    record Command(String sourceUrl) {

        public Command {
            if (sourceUrl == null || sourceUrl.isBlank()) {
                throw new IllegalArgumentException("sourceUrl must not be blank");
            }
        }
    }
}
