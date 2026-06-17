package dev.localassistant.assistant.adapters.outbound.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.assistant.config.AssistantMcpProperties;
import dev.localassistant.assistant.tools.CountriesPort;
import dev.localassistant.assistant.tools.WeatherPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration
@Profile("!ingest-rag")
@EnableConfigurationProperties(AssistantMcpProperties.class)
class McpOutboundConfiguration {

    @Bean
    CountriesMcpResponseMapper countriesMcpResponseMapper(ObjectMapper objectMapper) {
        return new CountriesMcpResponseMapper(objectMapper);
    }

    @Bean
    WeatherMcpResponseMapper weatherMcpResponseMapper(Clock clock) {
        return new WeatherMcpResponseMapper(clock);
    }

    @Bean
    CountriesPort countriesPort(
            McpToolInvoker mcpToolInvoker,
            CountriesMcpResponseMapper countriesMcpResponseMapper,
            AssistantMcpProperties assistantMcpProperties
    ) {
        return new CountriesMcpClientAdapter(mcpToolInvoker, countriesMcpResponseMapper, assistantMcpProperties);
    }

    @Bean
    WeatherPort weatherPort(
            McpToolInvoker mcpToolInvoker,
            WeatherMcpResponseMapper weatherMcpResponseMapper,
            AssistantMcpProperties assistantMcpProperties
    ) {
        return new WeatherMcpClientAdapter(mcpToolInvoker, weatherMcpResponseMapper, assistantMcpProperties);
    }
}
