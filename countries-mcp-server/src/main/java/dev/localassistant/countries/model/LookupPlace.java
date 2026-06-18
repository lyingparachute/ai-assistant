package dev.localassistant.countries.model;

import org.apache.commons.lang3.StringUtils;

public record LookupPlace(String value) {

    public LookupPlace {
        final var normalized = StringUtils.stripToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("lookup place must not be blank");
        }
        value = normalized;
    }

    public static LookupPlace of(String value) {
        return new LookupPlace(value);
    }
}
