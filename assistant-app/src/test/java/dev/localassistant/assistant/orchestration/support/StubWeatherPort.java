package dev.localassistant.assistant.orchestration.support;

import dev.localassistant.assistant.tools.ToolExecutionResult;
import dev.localassistant.assistant.tools.WeatherPort;
import dev.localassistant.assistant.tools.WeatherReport;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class StubWeatherPort implements WeatherPort {

    private final Map<String, ToolExecutionResult<WeatherReport>> responses = new HashMap<>();
    private int invocationCount;

    public StubWeatherPort register(String location, ToolExecutionResult<WeatherReport> result) {
        responses.put(location, Objects.requireNonNull(result));
        return this;
    }

    @Override
    public ToolExecutionResult<WeatherReport> currentWeather(String location) {
        invocationCount++;
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
