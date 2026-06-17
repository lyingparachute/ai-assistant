package dev.localassistant.assistant.tools;

import java.util.Objects;

public record WeatherReport(Location location, Temperature temperature, WeatherTimestamp timestamp) {

    public WeatherReport {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
