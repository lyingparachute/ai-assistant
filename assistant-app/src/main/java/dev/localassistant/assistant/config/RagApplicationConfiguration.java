package dev.localassistant.assistant.config;

import dev.localassistant.assistant.adapters.inbound.cli.RagIngestionCommand;
import dev.localassistant.assistant.rag.DeterministicTextChunker;
import dev.localassistant.assistant.rag.RagIngestionUseCase;
import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.rag.ProductKnowledgePort;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({
        AssistantRagStorageProperties.class,
        AssistantRagRetrievalProperties.class,
        AssistantRagChunkingProperties.class
})
class RagApplicationConfiguration {

    @Bean
    DeterministicTextChunker deterministicTextChunker(AssistantRagChunkingProperties chunkingProperties) {
        return new DeterministicTextChunker(
                chunkingProperties.chunkMaxSize(), chunkingProperties.chunkOverlap());
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
    @ConditionalOnBean(RagIngestionUseCase.class)
    RagIngestionCommand ragIngestionCommand(
            ConfigurableApplicationContext applicationContext,
            RagIngestionUseCase ragIngestionUseCase,
            AssistantRagRetrievalProperties retrievalProperties) {
        return new RagIngestionCommand(applicationContext, ragIngestionUseCase, retrievalProperties);
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
