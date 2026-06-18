package dev.localassistant.assistant.weather.domain;

import java.time.Instant;
import java.util.Objects;

public sealed interface WeatherTimestamp permits WeatherTimestamp.Observed, WeatherTimestamp.Retrieved {

    Instant value();

    record Observed(Instant value) implements WeatherTimestamp {
        public Observed {
            Objects.requireNonNull(value, "value");
        }
    }

    record Retrieved(Instant value) implements WeatherTimestamp {
        public Retrieved {
            Objects.requireNonNull(value, "value");
        }
    }
}
