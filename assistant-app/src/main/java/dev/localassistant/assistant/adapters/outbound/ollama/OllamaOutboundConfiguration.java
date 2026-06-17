package dev.localassistant.assistant.adapters.outbound.ollama;

import dev.localassistant.assistant.config.AssistantEmbeddingProperties;
import dev.localassistant.assistant.llm.EmbeddingPort;
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
class OllamaOutboundConfiguration {

    @Bean
    EmbeddingModel ollamaEmbeddingModel(AssistantEmbeddingProperties assistantEmbeddingProperties) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(assistantEmbeddingProperties.ollamaBaseUrl())
                .build();
        OllamaEmbeddingOptions options = OllamaEmbeddingOptions.builder()
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
