package dev.localassistant.countries.model;

import org.apache.commons.lang3.StringUtils;

public record CountryFacts(
        String countryName,
        String capital,
        String region,
        long population
) {

    public CountryFacts {
        if (StringUtils.isBlank(countryName)) {
            throw new IllegalArgumentException("countryName must not be blank");
        }
        if (StringUtils.isBlank(capital)) {
            throw new IllegalArgumentException("capital must not be blank");
        }
        if (StringUtils.isBlank(region)) {
            throw new IllegalArgumentException("region must not be blank");
        }
        if (population < 0) {
            throw new IllegalArgumentException("population must not be negative");
        }
    }
}
