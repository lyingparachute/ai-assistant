package dev.localassistant.assistant.rag.infrastructure.config;

import dev.localassistant.assistant.rag.domain.DeterministicTextChunker;
import dev.localassistant.assistant.rag.domain.RagIngestion;
import dev.localassistant.assistant.rag.domain.port.inbound.IngestRag;
import dev.localassistant.assistant.rag.domain.port.inbound.RetrieveRagKnowledge;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeChunkStore;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeEmbedding;
import dev.localassistant.assistant.rag.domain.port.outbound.ProductPageSource;
import dev.localassistant.assistant.rag.infrastructure.RagRetrieval;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({
    AssistantRagStorageProperties.class,
    AssistantRagRetrievalProperties.class,
    AssistantRagChunkingProperties.class
})
class RagUseCaseConfiguration {

    @Bean
    DeterministicTextChunker deterministicTextChunker(final AssistantRagChunkingProperties chunkingProperties) {
        return new DeterministicTextChunker(chunkingProperties.chunkingWindow());
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    @Profile("ingest-rag")
    @ConditionalOnMissingBean(IngestRag.class)
    IngestRag ingestRag(
        final KnowledgeEmbedding knowledgeEmbedding,
        final KnowledgeChunkStore knowledgeChunkStore,
        final ProductPageSource productPageSource,
        final DeterministicTextChunker deterministicTextChunker,
        final Clock clock) {
        return new RagIngestion(
            knowledgeEmbedding,
            knowledgeChunkStore,
            productPageSource,
            deterministicTextChunker,
            clock);
    }

    @Bean
    @Profile("!ingest-rag")
    @ConditionalOnMissingBean(RetrieveRagKnowledge.class)
    RetrieveRagKnowledge retrieveRagKnowledge(
        final KnowledgeEmbedding knowledgeEmbedding, final KnowledgeChunkStore knowledgeChunkStore) {
        return new RagRetrieval(knowledgeEmbedding, knowledgeChunkStore);
    }
}
