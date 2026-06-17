package dev.localassistant.assistant.rag.support;

import dev.localassistant.assistant.rag.ProductKnowledgePort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

@TestConfiguration
public class RagIngestionTestConfiguration {

    public static final String FIXTURE_HTML = "fixtures/rag/cdq-fraud-guard-sample.html";

    @Bean
    @Primary
    AtomicReference<FixtureProductKnowledgePort> fixtureProductKnowledgePortHolder() throws IOException {
        return new AtomicReference<>(FixtureProductKnowledgePort.fromClasspathHtml(FIXTURE_HTML));
    }

    @Bean
    @Primary
    ProductKnowledgePort fixtureProductKnowledgePort(
            AtomicReference<FixtureProductKnowledgePort> fixtureProductKnowledgePortHolder) {
        return sourceUrl -> fixtureProductKnowledgePortHolder.get().fetchAndExtract(sourceUrl);
    }
}
