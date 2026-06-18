package dev.localassistant.assistant.answering.api.http;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@Profile("!ingest-rag")
@EnableConfigurationProperties({AssistantCorsProperties.class, AssistantChatProperties.class})
class HttpInboundConfiguration implements WebMvcConfigurer {

    private final AssistantCorsProperties corsProperties;

    HttpInboundConfiguration(final AssistantCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean(name = "chatStreamExecutor")
    Executor chatStreamExecutor(final AssistantChatProperties chatProperties) {
        final var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(chatProperties.poolSize());
        executor.setMaxPoolSize(chatProperties.poolSize());
        executor.setQueueCapacity(chatProperties.queueCapacity());
        executor.setThreadNamePrefix("chat-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
            .allowedMethods(corsProperties.allowedMethods().toArray(String[]::new))
            .allowedHeaders(corsProperties.allowedHeaders().toArray(String[]::new));
    }
}
