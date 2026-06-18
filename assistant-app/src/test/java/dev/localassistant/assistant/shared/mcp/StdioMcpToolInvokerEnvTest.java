package dev.localassistant.assistant.shared.mcp;

import dev.localassistant.assistant.shared.mcp.infrastructure.config.AssistantMcpProperties.McpServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class StdioMcpToolInvokerEnvTest {

    private static McpServer server(Map<String, String> staticEnv, List<String> passthrough) {
        return new McpServer(
                "java",
                List.of(),
                staticEnv,
                passthrough,
                "stdio",
                60,
                "any-tool"
        );
    }

    @Test
    void forwardsConfiguredPassthroughKeyPresentInHostEnv() {
        McpServer server = server(Map.of(), List.of("REST_COUNTRIES_API_KEY"));
        Function<String, String> hostEnv = Map.of("REST_COUNTRIES_API_KEY", "secret-value")::get;

        Map<String, String> resolved = StdioMcpToolInvoker.resolveProcessEnv(server, hostEnv);

        assertThat(resolved).containsEntry("REST_COUNTRIES_API_KEY", "secret-value");
    }

    @Test
    void forwardsNothingWhenPassthroughEmpty() {
        McpServer server = server(Map.of("STATIC", "kept"), List.of());
        Function<String, String> hostEnv = Map.of("REST_COUNTRIES_API_KEY", "secret-value")::get;

        Map<String, String> resolved = StdioMcpToolInvoker.resolveProcessEnv(server, hostEnv);

        assertThat(resolved).containsExactlyInAnyOrderEntriesOf(Map.of("STATIC", "kept"));
    }

    @Test
    void doesNotOverrideKeyAlreadySetNonBlankInStaticEnv() {
        McpServer server = server(Map.of("REST_COUNTRIES_API_KEY", "config-value"), List.of("REST_COUNTRIES_API_KEY"));
        Function<String, String> hostEnv = Map.of("REST_COUNTRIES_API_KEY", "host-value")::get;

        Map<String, String> resolved = StdioMcpToolInvoker.resolveProcessEnv(server, hostEnv);

        assertThat(resolved).containsEntry("REST_COUNTRIES_API_KEY", "config-value");
    }

    @Test
    void skipsConfiguredKeyAbsentFromHostEnv() {
        McpServer server = server(Map.of(), List.of("REST_COUNTRIES_API_KEY"));
        Function<String, String> hostEnv = key -> null;

        Map<String, String> resolved = StdioMcpToolInvoker.resolveProcessEnv(server, hostEnv);

        assertThat(resolved).doesNotContainKey("REST_COUNTRIES_API_KEY");
    }

    @Test
    void overridesBlankStaticEnvValueWithHostValue() {
        McpServer server = server(Map.of("REST_COUNTRIES_API_KEY", "  "), List.of("REST_COUNTRIES_API_KEY"));
        Function<String, String> hostEnv = Map.of("REST_COUNTRIES_API_KEY", "host-value")::get;

        Map<String, String> resolved = StdioMcpToolInvoker.resolveProcessEnv(server, hostEnv);

        assertThat(resolved).containsEntry("REST_COUNTRIES_API_KEY", "host-value");
    }
}
