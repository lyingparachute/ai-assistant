package dev.localassistant.countries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.countries.application.LookupCountryUseCase;
import dev.localassistant.countries.model.CountryFacts;
import dev.localassistant.countries.model.CountryLookupOutcome;
import dev.localassistant.countries.schemas.CountryToolSchemas;
import dev.localassistant.countries.support.errors.CountryToolErrors;
import dev.localassistant.countries.tools.CountryLookupTool;
import dev.localassistant.countries.tools.CountryToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CountryToolContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pinsToolNameInputSchemaAndRequiredFields() {
        CountryLookupTool countryLookupTool = new CountryLookupTool(
                mock(dev.localassistant.countries.application.LookupCountryUseCase.class),
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
        JsonNode payload = handleOutcome(new CountryLookupOutcome.SourceUnavailable());

        assertThat(payload.path("ok").asBoolean()).isFalse();
        assertThat(payload.path("error").asText()).isEqualTo(CountryToolErrors.ERROR_SOURCE_UNAVAILABLE);
        assertThat(payload.path("hint").asText()).isEqualTo(CountryToolErrors.HINT_SOURCE_UNAVAILABLE);
    }

    @Test
    void ambiguousCapitalErrorEnvelopeMatchesContract() throws Exception {
        JsonNode payload =
                handleOutcome(new CountryLookupOutcome.AmbiguousCapital(java.util.List.of("Country A", "Country B")));

        assertThat(payload.path("ok").asBoolean()).isFalse();
        assertThat(payload.path("error").asText()).isEqualTo(CountryToolErrors.ERROR_AMBIGUOUS_CAPITAL);
        assertThat(payload.path("hint").asText())
                .isEqualTo("Capital matches multiple countries: Country A, Country B. Provide the country name instead.");
    }

    private JsonNode handleOutcome(CountryLookupOutcome outcome) throws Exception {
        LookupCountryUseCase lookupCountryUseCase = mock(LookupCountryUseCase.class);
        when(lookupCountryUseCase.lookup(any())).thenReturn(outcome);
        CountryLookupTool countryLookupTool = new CountryLookupTool(lookupCountryUseCase, objectMapper);

        McpSchema.CallToolResult result = countryLookupTool.handle(
                null,
                new McpSchema.CallToolRequest(CountryToolSchemas.TOOL_NAME, java.util.Map.of("name", "Anything"))
        );

        assertThat(result.isError()).isTrue();
        return objectMapper.readTree(((McpSchema.TextContent) result.content().getFirst()).text());
    }

    @Test
    void hintStringsAreByteIdentical() {
        assertThat(CountryToolErrors.HINT_NAME_REQUIRED)
                .isEqualTo("Provide a non-empty English country name or capital city.");
        assertThat(CountryToolErrors.HINT_NOT_RECOGNIZED)
                .isEqualTo("Provide an English country name or capital city such as Germany or Berlin.");
        assertThat(CountryToolErrors.HINT_SOURCE_UNAVAILABLE)
                .isEqualTo("REST Countries is unavailable. Retry the lookup later and do not invent country facts.");
        assertThat(CountryToolErrors.ambiguousCapitalHint(java.util.List.of("Country A", "Country B")))
                .isEqualTo("Capital matches multiple countries: Country A, Country B. Provide the country name instead.");
        assertThat(CountryToolErrors.HINT_INTERNAL_FAILURE)
                .isEqualTo("Retry the lookup later. If the problem persists, check server logs on stderr.");
    }

    @Test
    void serializationFailureReturnsInternalFailureEnvelopeWithoutRecursion() throws Exception {
        ObjectMapper throwingMapper = mock(ObjectMapper.class);
        when(throwingMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {
                });
        LookupCountryUseCase lookupCountryUseCase = mock(LookupCountryUseCase.class);
        when(lookupCountryUseCase.lookup(any())).thenReturn(new CountryLookupOutcome.SourceUnavailable());
        CountryLookupTool countryLookupTool = new CountryLookupTool(lookupCountryUseCase, throwingMapper);

        McpSchema.CallToolResult result = countryLookupTool.handle(
                null,
                new McpSchema.CallToolRequest(CountryToolSchemas.TOOL_NAME, java.util.Map.of("name", "Germany"))
        );

        assertThat(result.isError()).isTrue();
        JsonNode payload = objectMapper.readTree(((McpSchema.TextContent) result.content().getFirst()).text());
        assertThat(payload.path("ok").asBoolean()).isFalse();
        assertThat(payload.path("error").asText()).isEqualTo(CountryToolErrors.ERROR_INTERNAL_FAILURE);
        assertThat(payload.path("hint").asText()).isEqualTo(CountryToolErrors.HINT_INTERNAL_FAILURE);
    }

    @Test
    void blankNameReturnsValidationErrorEnvelope() {
        CountryLookupTool countryLookupTool = new CountryLookupTool(
                mock(dev.localassistant.countries.application.LookupCountryUseCase.class),
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
