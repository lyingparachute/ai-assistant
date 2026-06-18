package dev.localassistant.assistant.answering.api.http;

// Wire-decoupling DTO: deliberately mirrors the domain CountryInfo 1:1 so domain types stay
// off the HTTP contract and can evolve independently of the wire shape.
record CountryInfoResponse(
    String countryName, String capital, String region, long population) {
}
