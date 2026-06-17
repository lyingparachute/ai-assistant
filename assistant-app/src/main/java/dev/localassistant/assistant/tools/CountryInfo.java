package dev.localassistant.assistant.tools;

import java.util.Objects;

public record CountryInfo(String countryName, String capital, String region, long population) {

    public CountryInfo {
        Objects.requireNonNull(countryName, "countryName");
        Objects.requireNonNull(capital, "capital");
        Objects.requireNonNull(region, "region");
        if (countryName.isBlank() || capital.isBlank() || region.isBlank()) {
            throw new IllegalArgumentException("countryName, capital, and region must not be blank");
        }
        if (population < 0) {
            throw new IllegalArgumentException("population must not be negative");
        }
    }
}
