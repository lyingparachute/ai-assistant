package dev.localassistant.countries;

import dev.localassistant.countries.adapters.inbound.mcp.CountriesMcpServerAdapter;
import dev.localassistant.countries.core.CountriesMcpServerFactory;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CountriesMcpServerLifecycleTest {

    @Test
    void startCreatesServerAndStopClosesIt() {
        CountriesMcpServerFactory factory = mock(CountriesMcpServerFactory.class);
        McpSyncServer server = mock(McpSyncServer.class);
        when(factory.createServer()).thenReturn(server);

        CountriesMcpServerAdapter adapter = new CountriesMcpServerAdapter(factory);

        adapter.start();
        assertThat(adapter.isRunning()).isTrue();

        adapter.stop();
        assertThat(adapter.isRunning()).isFalse();
        verify(server).close();
    }

    @Test
    void repeatedStopIsIdempotent() {
        CountriesMcpServerFactory factory = mock(CountriesMcpServerFactory.class);
        McpSyncServer server = mock(McpSyncServer.class);
        when(factory.createServer()).thenReturn(server);

        CountriesMcpServerAdapter adapter = new CountriesMcpServerAdapter(factory);
        adapter.start();
        adapter.stop();
        adapter.stop();

        verify(server).close();
    }
}
