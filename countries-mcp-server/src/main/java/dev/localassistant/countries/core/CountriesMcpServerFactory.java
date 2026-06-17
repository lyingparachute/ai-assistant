package dev.localassistant.countries.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.countries.application.CountriesApplicationService;
import dev.localassistant.countries.config.CountriesMcpConfiguration;
import dev.localassistant.countries.tools.CountryLookupTool;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;

public final class CountriesMcpServerFactory {

    private final CountriesMcpConfiguration configuration;
    private final CountriesApplicationService countriesApplicationService;
    private final ObjectMapper objectMapper;

    public CountriesMcpServerFactory(
            CountriesMcpConfiguration configuration,
            CountriesApplicationService countriesApplicationService,
            ObjectMapper objectMapper
    ) {
        this.configuration = configuration;
        this.countriesApplicationService = countriesApplicationService;
        this.objectMapper = objectMapper;
    }

    public McpSyncServer createServer() {
        CountryLookupTool countryLookupTool = new CountryLookupTool(countriesApplicationService, objectMapper);
        McpJsonMapper mcpJsonMapper = new JacksonMcpJsonMapper(objectMapper);
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(mcpJsonMapper);

        return McpServer.sync(transportProvider)
                .serverInfo(configuration.serverName(), configuration.serverVersion())
                .requestTimeout(Duration.ofSeconds(configuration.restCountriesTimeoutSeconds() * 2L))
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .toolCall(countryLookupTool.toolDefinition(), countryLookupTool::handle)
                .build();
    }
}
