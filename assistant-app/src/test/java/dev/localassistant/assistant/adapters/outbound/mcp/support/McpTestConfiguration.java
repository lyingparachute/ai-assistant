package dev.localassistant.assistant.adapters.outbound.mcp.support;

import dev.localassistant.assistant.adapters.outbound.mcp.McpToolInvoker;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@TestConfiguration
public class McpTestConfiguration {

    public static final Instant FIXED_INSTANT = Instant.parse("2026-06-15T10:15:30Z");

    @Bean
    @Primary
    Clock testClock() {
        return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }

    @Bean
    @Primary
    StubMcpToolInvoker stubMcpToolInvoker() {
        return new StubMcpToolInvoker();
    }

    @Bean
    McpToolInvoker mcpToolInvoker(StubMcpToolInvoker stubMcpToolInvoker) {
        return stubMcpToolInvoker;
    }
}
