package dev.localassistant.assistant.shared.mcp.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!ingest-rag")
@EnableConfigurationProperties(AssistantMcpProperties.class)
class McpClientConfiguration {
}
