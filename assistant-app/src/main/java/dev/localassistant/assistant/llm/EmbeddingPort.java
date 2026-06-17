package dev.localassistant.assistant.llm;

public interface EmbeddingPort {

    EmbeddingResult embedDocument(String text);

    EmbeddingResult embedQuery(String text);
}
