package dev.localassistant.assistant.adapters.inbound.http;

public record CountryInfoResponse(
        String countryName, String capital, String region, long population) {}
