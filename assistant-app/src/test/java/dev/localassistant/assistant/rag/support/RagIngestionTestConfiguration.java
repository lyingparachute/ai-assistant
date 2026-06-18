package dev.localassistant.assistant.rag.support;

import dev.localassistant.assistant.rag.infrastructure.FixtureProductPageSource;
import dev.localassistant.assistant.rag.domain.port.outbound.ProductPageSource;
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
    AtomicReference<FixtureProductPageSource> fixtureProductPageSourceHolder() throws IOException {
        return new AtomicReference<>(FixtureProductPageSource.fromClasspathHtml(FIXTURE_HTML));
    }

    @Bean
    @Primary
    ProductPageSource fixtureProductPageSource(
            AtomicReference<FixtureProductPageSource> fixtureProductPageSourceHolder) {
        return command -> fixtureProductPageSourceHolder.get().fetchAndExtract(command);
    }
}
