package dev.localassistant.assistant.rag.infrastructure.support;

import dev.localassistant.assistant.rag.domain.port.outbound.EmbeddingPort;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeEmbedding;
import dev.localassistant.assistant.rag.infrastructure.OllamaKnowledgeEmbedding;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestEmbeddingPortConfiguration {

    @Bean
    @Primary
    EmbeddingPort deterministicTestEmbeddingPort() {
        return new DeterministicTestEmbeddingAdapter();
    }

    @Bean
    KnowledgeEmbedding knowledgeEmbedding(EmbeddingPort deterministicTestEmbeddingPort) {
        return new OllamaKnowledgeEmbedding(deterministicTestEmbeddingPort);
    }
}
