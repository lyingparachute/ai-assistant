package dev.localassistant.assistant.adapters.outbound.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CountriesMcpClientContractTest {

    private static final String TOOL_NAME = "country_lookup";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CountriesMcpResponseMapper mapper = new CountriesMcpResponseMapper(objectMapper);

    @Test
    void pinsToolNameAndRequiredInputField() {
        assertThat(TOOL_NAME).isEqualTo("country_lookup");
    }

    @Test
    void successEnvelopeMapsToCountryInfoFields() throws Exception {
        String json = """
                {
                  "ok": true,
                  "data": {
                    "countryName": "Germany",
                    "capital": "Berlin",
                    "region": "Europe",
                    "population": 83240525
                  }
                }
                """;

        JsonNode payload = objectMapper.readTree(json);
        assertThat(payload.path("ok").asBoolean()).isTrue();
        JsonNode data = payload.path("data");
        assertThat(data.fieldNames()).toIterable().containsExactly(
                "countryName",
                "capital",
                "region",
                "population"
        );
        assertThat(json).doesNotContain("official");
        assertThat(json).doesNotContain("cca2");

        var result = mapper.mapResponse(new McpToolInvoker.McpToolResponse(java.util.List.of(json), false));
        assertThat(result).isInstanceOf(dev.localassistant.assistant.tools.ToolExecutionResult.Success.class);
    }

    @Test
    void mcpErrorFlagWithUnparseableContentMapsToSourceUnavailable() {
        var result = mapper.mapResponse(
                new McpToolInvoker.McpToolResponse(java.util.List.of("upstream crashed"), true)
        );

        assertThat(result).isInstanceOf(dev.localassistant.assistant.tools.ToolExecutionResult.SourceUnavailable.class);
        var unavailable = (dev.localassistant.assistant.tools.ToolExecutionResult.SourceUnavailable<
                dev.localassistant.assistant.tools.CountryInfo>) result;
        assertThat(unavailable.sourceLabel()).isEqualTo(CountriesMcpResponseMapper.SOURCE_LABEL);
        assertThat(unavailable.message()).isEqualTo(CountriesMcpResponseMapper.MCP_ERROR_FLAG_MESSAGE);
    }

    @Test
    void errorEnvelopeShapeMatchesContract() throws Exception {
        String json = """
                {
                  "ok": false,
                  "error": "country name is not recognized",
                  "hint": "Provide an English country name or capital city such as Germany or Berlin."
                }
                """;

        JsonNode payload = objectMapper.readTree(json);
        assertThat(payload.path("ok").asBoolean()).isFalse();
        assertThat(payload.path("error").asText()).isEqualTo("country name is not recognized");
        assertThat(payload.path("hint").asText()).contains("Germany");
    }
}
