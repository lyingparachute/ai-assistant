package dev.localassistant.countries.model;

public record CountryFacts(
        String countryName,
        String capital,
        String region,
        long population
) {

    public CountryFacts {
        if (countryName == null || countryName.isBlank()) {
            throw new IllegalArgumentException("countryName must not be blank");
        }
        if (capital == null || capital.isBlank()) {
            throw new IllegalArgumentException("capital must not be blank");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("region must not be blank");
        }
        if (population < 0) {
            throw new IllegalArgumentException("population must not be negative");
        }
    }
}
