package dev.localassistant.assistant.rag.domain.ports.inbound;

import dev.localassistant.assistant.rag.ProductPageResult;

public interface ProductKnowledgePort {

    ProductPageResult fetchAndExtract(String sourceUrl);
}
