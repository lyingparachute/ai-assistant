package dev.localassistant.assistant.rag.infrastructure.config;

import dev.localassistant.assistant.config.AssistantEmbeddingProperties;
import dev.localassistant.assistant.rag.domain.port.outbound.EmbeddingPort;
import dev.localassistant.assistant.rag.infrastructure.OllamaEmbeddingAdapter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(AssistantEmbeddingProperties.class)
class OllamaEmbeddingConfiguration {

    @Bean
    EmbeddingModel ollamaEmbeddingModel(AssistantEmbeddingProperties assistantEmbeddingProperties) {
        final var ollamaApi = OllamaApi.builder()
                .baseUrl(assistantEmbeddingProperties.ollamaBaseUrl())
                .build();
        final var options = OllamaEmbeddingOptions.builder()
                .model(assistantEmbeddingProperties.modelName())
                .build();
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .build();
    }

    @Bean
    EmbeddingPort ollamaEmbeddingPort(
            EmbeddingModel ollamaEmbeddingModel, AssistantEmbeddingProperties assistantEmbeddingProperties) {
        return new OllamaEmbeddingAdapter(ollamaEmbeddingModel, assistantEmbeddingProperties);
    }
}
