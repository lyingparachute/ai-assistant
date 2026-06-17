package dev.localassistant.assistant.adapters.outbound.cdq;

import dev.localassistant.assistant.config.AssistantRagProperties;
import dev.localassistant.assistant.rag.ProductKnowledgePort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(AssistantRagProperties.class)
class CdqOutboundConfiguration {

    @Bean
    ProductPageFetcher productPageFetcher(AssistantRagProperties assistantRagProperties) {
        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        Duration timeout = Duration.ofSeconds(assistantRagProperties.fetchTimeoutSeconds());
        return new ProductPageFetcher(httpClient, timeout);
    }

    @Bean
    ProductPageTextExtractor productPageTextExtractor() {
        return new ProductPageTextExtractor();
    }

    @Bean
    ProductKnowledgePort productKnowledgePort(
            ProductPageFetcher productPageFetcher, ProductPageTextExtractor productPageTextExtractor) {
        return new CdqProductKnowledgeAdapter(productPageFetcher, productPageTextExtractor);
    }
}
