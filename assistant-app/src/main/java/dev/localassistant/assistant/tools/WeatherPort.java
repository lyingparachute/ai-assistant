package dev.localassistant.assistant.tools;

public interface WeatherPort {

    ToolExecutionResult<WeatherReport> currentWeather(String location);
}
