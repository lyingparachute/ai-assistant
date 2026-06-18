package dev.localassistant.assistant.rag.domain.port.outbound;

import dev.localassistant.assistant.rag.domain.ProductPageResult;

public interface ProductPageSource {

    ProductPageResult fetchAndExtract(Command command);

    record Command(String sourceUrl) {

        public Command {
            if (sourceUrl == null || sourceUrl.isBlank()) {
                throw new IllegalArgumentException("sourceUrl must not be blank");
            }
        }
    }
}
