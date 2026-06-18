package dev.localassistant.assistant.synthesis.infrastructure.config;

import dev.localassistant.assistant.synthesis.domain.port.outbound.LlmPort;
import dev.localassistant.assistant.synthesis.infrastructure.OllamaLlmAdapter;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(AssistantLlmProperties.class)
class OllamaSynthesisConfiguration {

    @Bean
    LlmPort ollamaLlmPort(AssistantLlmProperties assistantLlmProperties) {
        final var chatTimeout = Duration.ofSeconds(assistantLlmProperties.timeoutSeconds());
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(chatTimeout);
        requestFactory.setReadTimeout(chatTimeout);
        final var streamingHttpClient =
                HttpClient.newBuilder().connectTimeout(chatTimeout).build();
        final var webClientBuilder =
                WebClient.builder().clientConnector(new JdkClientHttpConnector(streamingHttpClient));
        final var ollamaApi =
                OllamaApi.builder()
                        .baseUrl(assistantLlmProperties.ollamaBaseUrl())
                        .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                        .webClientBuilder(webClientBuilder)
                        .build();
        final var options = OllamaChatOptions.builder()
                .model(assistantLlmProperties.modelName())
                .build();
        final var chatModel =
                OllamaChatModel.builder()
                        .ollamaApi(ollamaApi)
                        .defaultOptions(options)
                        .build();
        return new OllamaLlmAdapter(chatModel, chatTimeout);
    }
}
