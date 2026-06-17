package dev.localassistant.assistant.adapters.inbound.http;

public record WeatherReportResponse(
        LocationResponse location, TemperatureResponse temperature, TimestampResponse timestamp) {

    public record LocationResponse(String city) {}

    public record TemperatureResponse(double celsius) {}

    public record TimestampResponse(String kind, String value) {}
}
