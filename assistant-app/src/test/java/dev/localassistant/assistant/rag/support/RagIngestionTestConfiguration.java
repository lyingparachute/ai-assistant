package dev.localassistant.assistant.rag.support;

import dev.localassistant.assistant.rag.domain.DeterministicTextChunker;
import dev.localassistant.assistant.rag.domain.RagIngestion;
import dev.localassistant.assistant.rag.domain.port.inbound.IngestRag;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeChunkStore;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeEmbedding;
import dev.localassistant.assistant.rag.domain.port.outbound.ProductPageSource;
import dev.localassistant.assistant.rag.infrastructure.FixtureProductPageSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

@TestConfiguration
public class RagIngestionTestConfiguration {

    public static final String FIXTURE_HTML = "fixtures/rag/cdq-fraud-guard-sample.html";

    @Bean
    @Primary
    AtomicReference<FixtureProductPageSource> fixtureProductPageSourceHolder() throws IOException {
        return new AtomicReference<>(FixtureProductPageSource.fromClasspathHtml(FIXTURE_HTML));
    }

    @Bean
    @Primary
    ProductPageSource fixtureProductPageSource(
            AtomicReference<FixtureProductPageSource> fixtureProductPageSourceHolder) {
        return command -> fixtureProductPageSourceHolder.get().fetchAndExtract(command);
    }

    @Bean
    @ConditionalOnMissingBean(IngestRag.class)
    IngestRag ingestRag(
            KnowledgeEmbedding knowledgeEmbedding,
            KnowledgeChunkStore knowledgeChunkStore,
            ProductPageSource fixtureProductPageSource,
            DeterministicTextChunker deterministicTextChunker,
            Clock clock) {
        return new RagIngestion(
                knowledgeEmbedding,
                knowledgeChunkStore,
                fixtureProductPageSource,
                deterministicTextChunker,
                clock);
    }
}
