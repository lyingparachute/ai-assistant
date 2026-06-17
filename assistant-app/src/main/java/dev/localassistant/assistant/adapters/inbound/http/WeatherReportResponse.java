package dev.localassistant.assistant.adapters.inbound.http;

// Wire-decoupling DTO: deliberately mirrors the domain WeatherReport 1:1 so domain types stay
// off the HTTP contract and can evolve independently of the wire shape.
public record WeatherReportResponse(
        LocationResponse location, TemperatureResponse temperature, TimestampResponse timestamp) {

    public record LocationResponse(String city) {}

    public record TemperatureResponse(double celsius) {}

    public record TimestampResponse(String kind, String value) {}
}
