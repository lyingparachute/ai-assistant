package dev.localassistant.assistant.weather.infrastructure;

import dev.localassistant.assistant.shared.mcp.support.FixtureSupport;
import dev.localassistant.assistant.shared.mcp.support.McpTestConfiguration;
import dev.localassistant.assistant.shared.mcp.support.StubMcpToolInvoker;
import dev.localassistant.assistant.shared.ToolExecutionResult;
import dev.localassistant.assistant.shared.mcp.McpToolInvocationException;
import dev.localassistant.assistant.weather.domain.Location;
import dev.localassistant.assistant.weather.domain.WeatherReport;
import dev.localassistant.assistant.weather.domain.WeatherTimestamp;
import dev.localassistant.assistant.weather.domain.port.inbound.ResolveWeatherObservation;
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
class WeatherMcpClientAdapterIntegrationTest {

    private static final String TOOL_NAME = "get-weather";

    @Autowired
    private ResolveWeatherObservation resolveWeatherObservation;

    @Autowired
    private StubMcpToolInvoker stubMcpToolInvoker;

    @BeforeEach
    void resetStub() {
        stubMcpToolInvoker.reset().when(TOOL_NAME, arguments -> StubMcpToolInvoker.textResponse(
                FixtureSupport.readFixture("fixtures/mcp/weather/munich-success.txt")
        ));
    }

    @Test
    void munichLookupMapsControlledFixtureToWeatherReport() {
        ToolExecutionResult<WeatherReport> result =
                resolveWeatherObservation.execute(
                        new ResolveWeatherObservation.Command(Location.of("Munich")));

        assertThat(result).isInstanceOf(ToolExecutionResult.Success.class);
        WeatherReport report = ((ToolExecutionResult.Success<WeatherReport>) result).value();
        assertThat(report.location().city()).isEqualTo("Munich");
        assertThat(report.temperature().celsius()).isEqualTo(12.3);
        assertThat(report.timestamp()).isInstanceOf(WeatherTimestamp.Retrieved.class);
        assertThat(report.timestamp().value()).isEqualTo(McpTestConfiguration.FIXED_INSTANT);
    }

    @Test
    void surroundingWhitespaceTrimmedBeforeMcpCallAndInReportLocation() {
        java.util.concurrent.atomic.AtomicReference<String> requestedCity =
                new java.util.concurrent.atomic.AtomicReference<>();
        stubMcpToolInvoker.when(TOOL_NAME, arguments -> {
            requestedCity.set(arguments.get("city").toString());
            return StubMcpToolInvoker.textResponse(
                    FixtureSupport.readFixture("fixtures/mcp/weather/munich-success.txt")
            );
        });

        ToolExecutionResult<WeatherReport> result =
                resolveWeatherObservation.execute(
                        new ResolveWeatherObservation.Command(Location.of("  Munich  ")));

        assertThat(requestedCity.get()).isEqualTo("Munich");
        assertThat(result).isInstanceOf(ToolExecutionResult.Success.class);
        WeatherReport report = ((ToolExecutionResult.Success<WeatherReport>) result).value();
        assertThat(report.location().city()).isEqualTo("Munich");
    }

    @Test
    void missingServerConfigurationMapsToSourceUnavailable() {
        stubMcpToolInvoker.fail(
                TOOL_NAME,
                new McpToolInvocationException("weather MCP server is not configured")
        );

        ToolExecutionResult<WeatherReport> result =
                resolveWeatherObservation.execute(
                        new ResolveWeatherObservation.Command(Location.of("Munich")));

        assertThat(result).isInstanceOf(ToolExecutionResult.SourceUnavailable.class);
        ToolExecutionResult.SourceUnavailable<WeatherReport> unavailable =
                (ToolExecutionResult.SourceUnavailable<WeatherReport>) result;
        assertThat(unavailable.sourceLabel()).isEqualTo("weather MCP");
    }

    @Test
    void malformedPayloadDoesNotInventTemperature() {
        stubMcpToolInvoker.when(TOOL_NAME, arguments -> StubMcpToolInvoker.textResponse(
                FixtureSupport.readFixture("fixtures/mcp/weather/malformed.txt")
        ));

        ToolExecutionResult<WeatherReport> result =
                resolveWeatherObservation.execute(
                        new ResolveWeatherObservation.Command(Location.of("Munich")));

        assertThat(result).isInstanceOf(ToolExecutionResult.SourceUnavailable.class);
        ToolExecutionResult.SourceUnavailable<WeatherReport> unavailable =
                (ToolExecutionResult.SourceUnavailable<WeatherReport>) result;
        assertThat(unavailable.message()).isEqualTo(WeatherMcpResponseMapper.MALFORMED_PAYLOAD_MESSAGE);
    }

    @Test
    void providerErrorDoesNotInventTemperature() {
        stubMcpToolInvoker.when(TOOL_NAME, arguments -> StubMcpToolInvoker.textResponse(
                FixtureSupport.readFixture("fixtures/mcp/weather/provider-error.txt")
        ));

        ToolExecutionResult<WeatherReport> result =
                resolveWeatherObservation.execute(
                        new ResolveWeatherObservation.Command(Location.of("Munich")));

        assertThat(result).isInstanceOf(ToolExecutionResult.SourceUnavailable.class);
    }
}
