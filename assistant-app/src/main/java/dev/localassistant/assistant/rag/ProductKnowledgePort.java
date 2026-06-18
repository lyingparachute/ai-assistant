package dev.localassistant.assistant.rag;

public interface ProductKnowledgePort {

    ProductPageResult fetchAndExtract(String sourceUrl);
}
