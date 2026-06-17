package dev.localassistant.assistant.adapters.inbound.http;

import dev.localassistant.assistant.config.AssistantCorsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(AssistantCorsProperties.class)
class HttpInboundConfiguration implements WebMvcConfigurer {

    private final AssistantCorsProperties corsProperties;

    HttpInboundConfiguration(AssistantCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods(corsProperties.allowedMethods().toArray(String[]::new))
                .allowedHeaders(corsProperties.allowedHeaders().toArray(String[]::new));
    }
}
