package dev.localassistant.countries.adapters.inbound.mcp;

import dev.localassistant.countries.core.CountriesMcpServerFactory;
import io.modelcontextprotocol.server.McpSyncServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "countries.mcp", name = "stdio-enabled", havingValue = "true")
public final class CountriesMcpServerAdapter implements SmartLifecycle {

    private final CountriesMcpServerFactory countriesMcpServerFactory;
    private volatile boolean running;
    private volatile boolean shutdownHookRegistered;
    private McpSyncServer server;

    @Override
    public void start() {
        if (running) {
            return;
        }
        server = countriesMcpServerFactory.createServer();
        registerShutdownHookOnce();
        running = true;
        log.info("Countries MCP server started on stdio transport");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        if (server != null) {
            server.close();
            server = null;
        }
        running = false;
        log.info("Countries MCP server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    private void registerShutdownHookOnce() {
        if (shutdownHookRegistered) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "countries-mcp-shutdown"));
        shutdownHookRegistered = true;
    }
}
