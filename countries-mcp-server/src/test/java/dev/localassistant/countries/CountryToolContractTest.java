package dev.localassistant.countries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.countries.model.CountryFacts;
import dev.localassistant.countries.model.CountryLookupOutcome;
import dev.localassistant.countries.schemas.CountryToolSchemas;
import dev.localassistant.countries.support.errors.CountryToolErrors;
import dev.localassistant.countries.tools.CountryLookupTool;
import dev.localassistant.countries.tools.CountryToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CountryToolContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pinsToolNameInputSchemaAndRequiredFields() {
        CountryLookupTool countryLookupTool = new CountryLookupTool(
                mock(dev.localassistant.countries.application.CountriesApplicationService.class),
                objectMapper
        );

        McpSchema.Tool tool = countryLookupTool.toolDefinition();

        assertThat(tool.name()).isEqualTo(CountryToolSchemas.TOOL_NAME);
        assertThat(tool.description()).contains("country");
        assertThat(tool.inputSchema().type()).isEqualTo("object");
        assertThat(tool.inputSchema().properties()).containsKey("name");
        assertThat(tool.inputSchema().required()).containsExactly("name");
    }

    @Test
    void successEnvelopeContainsCompactFieldsWithoutUpstreamJson() throws Exception {
        String json = objectMapper.writeValueAsString(
                CountryToolResult.success(new CountryFacts("Germany", "Berlin", "Europe", 83_240_525L))
        );

        JsonNode payload = objectMapper.readTree(json);
        assertThat(payload.path("ok").asBoolean()).isTrue();
        JsonNode data = payload.path("data");
        assertThat(data.fieldNames()).toIterable().containsExactly(
                "countryName",
                "capital",
                "region",
                "population"
        );
        assertThat(data.path("countryName").asText()).isEqualTo("Germany");
        assertThat(data.path("capital").asText()).isEqualTo("Berlin");
        assertThat(data.path("region").asText()).isEqualTo("Europe");
        assertThat(data.path("population").asLong()).isEqualTo(83_240_525L);
        assertThat(json).doesNotContain("official");
        assertThat(json).doesNotContain("cca2");
    }

    @Test
    void notRecognizedErrorEnvelopeMatchesContract() throws Exception {
        String json = objectMapper.writeValueAsString(CountryToolErrors.notRecognized());

        JsonNode payload = objectMapper.readTree(json);
        assertThat(payload.path("ok").asBoolean()).isFalse();
        assertThat(payload.path("error").asText()).isEqualTo(CountryToolErrors.ERROR_NOT_RECOGNIZED);
        assertThat(payload.path("hint").asText()).isEqualTo(CountryToolErrors.HINT_NOT_RECOGNIZED);
    }

    @Test
    void sourceUnavailableErrorEnvelopeMatchesContract() throws Exception {
        String json = objectMapper.writeValueAsString(
                CountryToolResult.fromOutcome(new CountryLookupOutcome.SourceUnavailable(
                        dev.localassistant.countries.application.CountryLookupHints.SOURCE_UNAVAILABLE
                ))
        );

        JsonNode payload = objectMapper.readTree(json);
        assertThat(payload.path("error").asText()).isEqualTo(CountryToolErrors.ERROR_SOURCE_UNAVAILABLE);
        assertThat(payload.path("hint").asText()).isEqualTo(
                dev.localassistant.countries.application.CountryLookupHints.SOURCE_UNAVAILABLE
        );
    }

    @Test
    void ambiguousCapitalErrorEnvelopeMatchesContract() throws Exception {
        String json = objectMapper.writeValueAsString(
                CountryToolResult.fromOutcome(new CountryLookupOutcome.AmbiguousCapital(
                        java.util.List.of("Country A", "Country B"),
                        "Capital matches multiple countries: Country A, Country B. Provide the country name instead."
                ))
        );

        JsonNode payload = objectMapper.readTree(json);
        assertThat(payload.path("error").asText()).isEqualTo(CountryToolErrors.ERROR_AMBIGUOUS_CAPITAL);
    }

    @Test
    void blankNameReturnsValidationErrorEnvelope() {
        CountryLookupTool countryLookupTool = new CountryLookupTool(
                mock(dev.localassistant.countries.application.CountriesApplicationService.class),
                objectMapper
        );

        McpSchema.CallToolResult result = countryLookupTool.handle(
                null,
                new McpSchema.CallToolRequest(CountryToolSchemas.TOOL_NAME, java.util.Map.of("name", " "))
        );

        assertThat(result.isError()).isTrue();
        String json = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertThat(json).contains("\"ok\":false");
        assertThat(json).contains(CountryToolErrors.ERROR_NAME_REQUIRED);
    }
}
