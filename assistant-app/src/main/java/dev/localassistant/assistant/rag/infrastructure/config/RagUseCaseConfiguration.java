package dev.localassistant.assistant.rag.infrastructure.config;

import dev.localassistant.assistant.config.AssistantRagChunkingProperties;
import dev.localassistant.assistant.config.AssistantRagRetrievalProperties;
import dev.localassistant.assistant.config.AssistantRagStorageProperties;
import dev.localassistant.assistant.rag.domain.DeterministicTextChunker;
import dev.localassistant.assistant.rag.domain.RagIngestion;
import dev.localassistant.assistant.rag.domain.port.inbound.IngestRag;
import dev.localassistant.assistant.rag.domain.port.inbound.RetrieveRagKnowledge;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeChunkStore;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeEmbedding;
import dev.localassistant.assistant.rag.domain.port.outbound.ProductPageSource;
import dev.localassistant.assistant.rag.infrastructure.RagRetrieval;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({
        AssistantRagStorageProperties.class,
        AssistantRagRetrievalProperties.class,
        AssistantRagChunkingProperties.class
})
public class RagUseCaseConfiguration {

    @Bean
    DeterministicTextChunker deterministicTextChunker(AssistantRagChunkingProperties chunkingProperties) {
        return new DeterministicTextChunker(
                chunkingProperties.chunkMaxSize(), chunkingProperties.chunkOverlap());
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnBean({KnowledgeEmbedding.class, KnowledgeChunkStore.class, ProductPageSource.class})
    @ConditionalOnMissingBean(IngestRag.class)
    IngestRag ingestRag(
            KnowledgeEmbedding knowledgeEmbedding,
            KnowledgeChunkStore knowledgeChunkStore,
            ProductPageSource productPageSource,
            DeterministicTextChunker deterministicTextChunker,
            Clock clock) {
        return new RagIngestion(
                knowledgeEmbedding,
                knowledgeChunkStore,
                productPageSource,
                deterministicTextChunker,
                clock);
    }

    @Bean
    @ConditionalOnBean({KnowledgeEmbedding.class, KnowledgeChunkStore.class})
    @ConditionalOnMissingBean(RetrieveRagKnowledge.class)
    RetrieveRagKnowledge retrieveRagKnowledge(
            KnowledgeEmbedding knowledgeEmbedding, KnowledgeChunkStore knowledgeChunkStore) {
        return new RagRetrieval(knowledgeEmbedding, knowledgeChunkStore);
    }
}
