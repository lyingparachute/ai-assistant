package dev.localassistant.assistant.adapters.outbound.ollama;

import dev.localassistant.assistant.config.AssistantEmbeddingProperties;
import dev.localassistant.assistant.config.AssistantLlmProperties;
import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.llm.LlmPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@Profile("!test")
@EnableConfigurationProperties({AssistantEmbeddingProperties.class, AssistantLlmProperties.class})
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

    @Bean
    LlmPort ollamaLlmPort(AssistantLlmProperties assistantLlmProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(
                java.time.Duration.ofSeconds(assistantLlmProperties.timeoutSeconds()));
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(assistantLlmProperties.timeoutSeconds()));
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(assistantLlmProperties.ollamaBaseUrl())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(assistantLlmProperties.modelName())
                .build();
        ChatModel chatModel =
                OllamaChatModel.builder()
                        .ollamaApi(ollamaApi)
                        .defaultOptions(options)
                        .build();
        return new OllamaLlmAdapter(chatModel);
    }
}
