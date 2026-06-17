package dev.localassistant.countries.model;

public record LookupPlace(String value) {

    public LookupPlace {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("lookup place must not be blank");
        }
        value = value.strip();
    }

    public static LookupPlace of(String value) {
        return new LookupPlace(value);
    }
}
