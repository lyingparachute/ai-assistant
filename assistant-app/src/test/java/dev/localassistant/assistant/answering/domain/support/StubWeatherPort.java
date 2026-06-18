package dev.localassistant.assistant.answering.domain.support;

import dev.localassistant.assistant.shared.ToolExecutionResult;
import dev.localassistant.assistant.weather.domain.WeatherReport;
import dev.localassistant.assistant.weather.domain.port.inbound.ResolveWeatherObservation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class StubWeatherPort implements ResolveWeatherObservation {

    private final Map<String, ToolExecutionResult<WeatherReport>> responses = new HashMap<>();
    private int invocationCount;

    public StubWeatherPort register(String location, ToolExecutionResult<WeatherReport> result) {
        responses.put(location, Objects.requireNonNull(result));
        return this;
    }

    @Override
    public ToolExecutionResult<WeatherReport> execute(ResolveWeatherObservation.Command command) {
        invocationCount++;
        final var location = command.location().city();
        ToolExecutionResult<WeatherReport> result = responses.get(location);
        if (result == null) {
            return new ToolExecutionResult.SourceUnavailable<>(
                    "Weather MCP", "No stub response for " + location, "Register a stub response");
        }
        return result;
    }

    public int invocationCount() {
        return invocationCount;
    }
}
