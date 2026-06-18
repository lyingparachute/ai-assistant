package dev.localassistant.assistant.answering.api.http;

// Wire-decoupling DTO: deliberately mirrors the domain WeatherReport 1:1 so domain types stay
// off the HTTP contract and can evolve independently of the wire shape.
record WeatherReportResponse(
    LocationResponse location, TemperatureResponse temperature, TimestampResponse timestamp) {

    record LocationResponse(String city) {
    }

    record TemperatureResponse(double celsius) {
    }

    record TimestampResponse(String kind, String value) {
    }
}
