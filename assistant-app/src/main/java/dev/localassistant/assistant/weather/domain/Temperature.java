package dev.localassistant.assistant.weather.domain;

public record Temperature(double celsius) {

    public Temperature {
        if (Double.isNaN(celsius) || Double.isInfinite(celsius)) {
            throw new IllegalArgumentException("celsius must be a finite number");
        }
    }

    public static Temperature celsius(final double value) {
        return new Temperature(value);
    }
}
