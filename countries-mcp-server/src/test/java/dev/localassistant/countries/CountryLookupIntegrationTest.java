package dev.localassistant.countries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.countries.application.LookupCountryUseCase;
import dev.localassistant.countries.config.CountriesMcpConfiguration;
import dev.localassistant.countries.schemas.CountryToolSchemas;
import dev.localassistant.countries.support.StubRestCountriesServer;
import dev.localassistant.countries.support.errors.CountryToolErrors;
import dev.localassistant.countries.tools.CountryLookupTool;
import dev.localassistant.countries.adapters.outbound.restcountries.RestCountriesHttpAdapter;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CountryLookupIntegrationTest {

    private StubRestCountriesServer stubRestCountriesServer;
    private CountryLookupTool countryLookupTool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        stubRestCountriesServer = new StubRestCountriesServer();
        CountriesMcpConfiguration configuration = new CountriesMcpConfiguration(
                stubRestCountriesServer.baseUrl(),
                2,
                "countries-mcp-server-test",
                "test",
                20
        );
        RestCountriesHttpAdapter restCountriesPort = new RestCountriesHttpAdapter(
                configuration,
                HttpClient.newHttpClient(),
                objectMapper
        );
        countryLookupTool = new CountryLookupTool(new LookupCountryUseCase(restCountriesPort), objectMapper);
    }

    @AfterEach
    void tearDown() {
        stubRestCountriesServer.close();
    }

    @Test
    void germanyLookupThroughMcpToolReturnsRequiredFields() throws Exception {
        stubRestCountriesServer.stubNameLookup(
                "Germany",
                200,
                StubRestCountriesServer.readFixture("/fixtures/restcountries/germany-by-name.json")
        );

        McpSchema.CallToolResult result = countryLookupTool.handle(
                null,
                new McpSchema.CallToolRequest(CountryToolSchemas.TOOL_NAME, Map.of("name", "Germany"))
        );

        assertThat(result.isError()).isFalse();
        JsonNode data = readToolPayload(result).path("data");
        assertThat(data.path("countryName").asText()).isEqualTo("Germany");
        assertThat(data.path("capital").asText()).isEqualTo("Berlin");
        assertThat(data.path("region").asText()).isEqualTo("Europe");
        assertThat(data.path("population").asLong()).isEqualTo(83_240_525L);
    }

    @Test
    void berlinLookupThroughMcpToolResolvesGermany() throws Exception {
        stubRestCountriesServer.stubNameLookup("Berlin", 404, "[]");
        stubRestCountriesServer.stubCapitalLookup(
                "Berlin",
                200,
                StubRestCountriesServer.readFixture("/fixtures/restcountries/germany-by-capital.json")
        );

        McpSchema.CallToolResult result = countryLookupTool.handle(
                null,
                new McpSchema.CallToolRequest(CountryToolSchemas.TOOL_NAME, Map.of("name", "Berlin"))
        );

        assertThat(result.isError()).isFalse();
        JsonNode data = readToolPayload(result).path("data");
        assertThat(data.path("countryName").asText()).isEqualTo("Germany");
        assertThat(data.path("capital").asText()).isEqualTo("Berlin");
        assertThat(data.path("region").asText()).isEqualTo("Europe");
        assertThat(data.path("population").asLong()).isEqualTo(83_240_525L);
    }

    @Test
    void unrecognizedInputReturnsStructuredToolError() throws Exception {
        stubRestCountriesServer.stubNameLookup("Atlantis", 404, "[]");
        stubRestCountriesServer.stubCapitalLookup("Atlantis", 404, "[]");

        McpSchema.CallToolResult result = countryLookupTool.handle(
                null,
                new McpSchema.CallToolRequest(CountryToolSchemas.TOOL_NAME, Map.of("name", "Atlantis"))
        );

        assertThat(result.isError()).isTrue();
        JsonNode payload = readToolPayload(result);
        assertThat(payload.path("ok").asBoolean()).isFalse();
        assertThat(payload.path("error").asText()).isEqualTo(CountryToolErrors.ERROR_NOT_RECOGNIZED);
        assertThat(payload.path("hint").asText()).isNotBlank();
    }

    @Test
    void upstreamFailureReturnsSourceUnavailableToolError() throws Exception {
        stubRestCountriesServer.stubNameLookup("Germany", 503, "upstream unavailable");
        stubRestCountriesServer.stubCapitalLookup("Germany", 503, "upstream unavailable");

        McpSchema.CallToolResult result = countryLookupTool.handle(
                null,
                new McpSchema.CallToolRequest(CountryToolSchemas.TOOL_NAME, Map.of("name", "Germany"))
        );

        assertThat(result.isError()).isTrue();
        JsonNode payload = readToolPayload(result);
        assertThat(payload.path("ok").asBoolean()).isFalse();
        assertThat(payload.path("error").asText()).isEqualTo(CountryToolErrors.ERROR_SOURCE_UNAVAILABLE);
        assertThat(payload.path("hint").asText()).contains("Retry");
    }

    @Test
    void upstreamTimeoutReturnsSourceUnavailableToolError() throws Exception {
        stubRestCountriesServer.stubNameLookup(
                "Germany",
                200,
                StubRestCountriesServer.readFixture("/fixtures/restcountries/germany-by-name.json"),
                5_000
        );

        McpSchema.CallToolResult result = countryLookupTool.handle(
                null,
                new McpSchema.CallToolRequest(CountryToolSchemas.TOOL_NAME, Map.of("name", "Germany"))
        );

        assertThat(result.isError()).isTrue();
        JsonNode payload = readToolPayload(result);
        assertThat(payload.path("error").asText()).isEqualTo(CountryToolErrors.ERROR_SOURCE_UNAVAILABLE);
    }

    @Test
    void ambiguousCapitalReturnsStructuredToolError() throws Exception {
        stubRestCountriesServer.stubNameLookup("Shared City", 404, "[]");
        stubRestCountriesServer.stubCapitalLookup(
                "Shared City",
                200,
                StubRestCountriesServer.readFixture("/fixtures/restcountries/shared-capital.json")
        );

        McpSchema.CallToolResult result = countryLookupTool.handle(
                null,
                new McpSchema.CallToolRequest(CountryToolSchemas.TOOL_NAME, Map.of("name", "Shared City"))
        );

        assertThat(result.isError()).isTrue();
        JsonNode payload = readToolPayload(result);
        assertThat(payload.path("error").asText()).isEqualTo(CountryToolErrors.ERROR_AMBIGUOUS_CAPITAL);
        assertThat(payload.path("hint").asText()).contains("Country A");
        assertThat(payload.path("hint").asText()).contains("Country B");
    }

    @Test
    void missingPopulationMapsToSourceUnavailableNotZeroPopulationSuccess() throws Exception {
        String bodyMissingPopulation = """
                [{"name":{"common":"Germany"},"capital":["Berlin"],"region":"Europe"}]
                """;
        stubRestCountriesServer.stubNameLookup("Germany", 200, bodyMissingPopulation);
        stubRestCountriesServer.stubCapitalLookup("Germany", 200, bodyMissingPopulation);

        McpSchema.CallToolResult result = countryLookupTool.handle(
                null,
                new McpSchema.CallToolRequest(CountryToolSchemas.TOOL_NAME, Map.of("name", "Germany"))
        );

        assertThat(result.isError()).isTrue();
        JsonNode payload = readToolPayload(result);
        assertThat(payload.path("ok").asBoolean()).isFalse();
        assertThat(payload.path("error").asText()).isEqualTo(CountryToolErrors.ERROR_SOURCE_UNAVAILABLE);
        assertThat(payload.has("data")).isFalse();
    }

    private JsonNode readToolPayload(McpSchema.CallToolResult result) throws Exception {
        String json = ((McpSchema.TextContent) result.content().getFirst()).text();
        return objectMapper.readTree(json);
    }
}
