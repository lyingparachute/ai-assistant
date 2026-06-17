package dev.localassistant.assistant.adapters.outbound.pgvector.support;

import dev.localassistant.assistant.llm.EmbeddingPort;
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
}
