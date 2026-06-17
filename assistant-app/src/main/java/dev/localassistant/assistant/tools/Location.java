package dev.localassistant.assistant.tools;

import java.util.Objects;

public record Location(String city) {

    public Location {
        Objects.requireNonNull(city, "city");
        if (city.isBlank()) {
            throw new IllegalArgumentException("city must not be blank");
        }
    }

    public static Location of(String city) {
        return new Location(city);
    }
}
