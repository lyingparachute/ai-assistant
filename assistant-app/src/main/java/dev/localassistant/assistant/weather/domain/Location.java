package dev.localassistant.assistant.weather.domain;

import java.util.Objects;

public record Location(String city) {

    public Location {
        Objects.requireNonNull(city, "city");
        city = city.strip();
        if (city.isBlank()) {
            throw new IllegalArgumentException("city must not be blank");
        }
    }

    public static Location of(final String city) {
        return new Location(city);
    }
}
