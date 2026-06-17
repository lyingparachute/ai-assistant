package dev.localassistant.assistant.config;

import dev.localassistant.assistant.rag.DeterministicTextChunker;
import dev.localassistant.assistant.rag.RagIngestionUseCase;
import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.rag.ProductKnowledgePort;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AssistantRagProperties.class)
class RagApplicationConfiguration {

    @Bean
    DeterministicTextChunker deterministicTextChunker(AssistantRagProperties assistantRagProperties) {
        return new DeterministicTextChunker(
                assistantRagProperties.chunkMaxSize(), assistantRagProperties.chunkOverlap());
    }

    @Bean
    @ConditionalOnBean({EmbeddingPort.class, RagKnowledgePort.class, ProductKnowledgePort.class})
    RagIngestionUseCase ragIngestionUseCase(
            EmbeddingPort embeddingPort,
            RagKnowledgePort ragKnowledgePort,
            ProductKnowledgePort productKnowledgePort,
            DeterministicTextChunker deterministicTextChunker,
            Clock clock) {
        return new RagIngestionUseCase(
                embeddingPort,
                ragKnowledgePort,
                productKnowledgePort,
                deterministicTextChunker,
                clock);
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
