package dev.localassistant.assistant.adapters.outbound.mcp;

import dev.localassistant.assistant.adapters.outbound.mcp.support.FixtureSupport;
import dev.localassistant.assistant.adapters.outbound.mcp.support.McpTestConfiguration;
import dev.localassistant.assistant.adapters.outbound.mcp.support.StubMcpToolInvoker;
import dev.localassistant.assistant.tools.CountriesPort;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.ToolExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(McpTestConfiguration.class)
@org.springframework.test.context.ContextConfiguration(
        initializers = dev.localassistant.assistant.support.ChatPathPortStubs.class)
class CountriesMcpClientAdapterIntegrationTest {

    private static final String TOOL_NAME = "country_lookup";

    @Autowired
    private CountriesPort countriesPort;

    @Autowired
    private StubMcpToolInvoker stubMcpToolInvoker;

    @BeforeEach
    void resetStub() {
        stubMcpToolInvoker.reset().when(TOOL_NAME, arguments -> {
            String name = arguments.get("name").toString();
            if ("Germany".equalsIgnoreCase(name) || "Berlin".equalsIgnoreCase(name)) {
                return StubMcpToolInvoker.textResponse(
                        FixtureSupport.readFixture("fixtures/mcp/countries/germany-success.json")
                );
            }
            return StubMcpToolInvoker.textResponse(
                    FixtureSupport.readFixture("fixtures/mcp/countries/not-recognized.json")
            );
        });
    }

    @Test
    void germanyLookupReturnsBerlinFromControlledFixture() {
        ToolExecutionResult<CountryInfo> result = countriesPort.lookupCountry("Germany");

        assertThat(result).isInstanceOf(ToolExecutionResult.Success.class);
        CountryInfo countryInfo = ((ToolExecutionResult.Success<CountryInfo>) result).value();
        assertThat(countryInfo.countryName()).isEqualTo("Germany");
        assertThat(countryInfo.capital()).isEqualTo("Berlin");
        assertThat(countryInfo.region()).isEqualTo("Europe");
        assertThat(countryInfo.population()).isEqualTo(83_240_525L);
    }

    @Test
    void berlinLookupResolvesGermanyFromControlledFixture() {
        ToolExecutionResult<CountryInfo> result = countriesPort.lookupCountry("Berlin");

        assertThat(result).isInstanceOf(ToolExecutionResult.Success.class);
        CountryInfo countryInfo = ((ToolExecutionResult.Success<CountryInfo>) result).value();
        assertThat(countryInfo.countryName()).isEqualTo("Germany");
        assertThat(countryInfo.capital()).isEqualTo("Berlin");
    }

    @Test
    void transportFailureMapsToSourceUnavailableWithoutInventingFacts() {
        stubMcpToolInvoker.fail(
                TOOL_NAME,
                new McpToolInvocationException("countries MCP server is not configured")
        );

        ToolExecutionResult<CountryInfo> result = countriesPort.lookupCountry("Germany");

        assertThat(result).isInstanceOf(ToolExecutionResult.SourceUnavailable.class);
        ToolExecutionResult.SourceUnavailable<CountryInfo> unavailable =
                (ToolExecutionResult.SourceUnavailable<CountryInfo>) result;
        assertThat(unavailable.sourceLabel()).isEqualTo("countries MCP");
    }

    @Test
    void toolErrorDoesNotInventCountryFacts() {
        ToolExecutionResult<CountryInfo> result = countriesPort.lookupCountry("Atlantis");

        assertThat(result).isInstanceOf(ToolExecutionResult.ToolError.class);
        ToolExecutionResult.ToolError<CountryInfo> toolError = (ToolExecutionResult.ToolError<CountryInfo>) result;
        assertThat(toolError.error()).isEqualTo("country name is not recognized");
        assertThat(toolError.hint()).contains("Germany");
    }
}
