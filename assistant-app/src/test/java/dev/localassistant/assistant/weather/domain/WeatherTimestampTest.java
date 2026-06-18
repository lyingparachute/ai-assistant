package dev.localassistant.assistant.weather.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherTimestampTest {

    @Test
    void distinguishesObservedFromRetrieved() {
        Instant instant = Instant.parse("2026-06-15T10:00:00Z");

        WeatherTimestamp observed = new WeatherTimestamp.Observed(instant);
        WeatherTimestamp retrieved = new WeatherTimestamp.Retrieved(instant);

        assertThat(observed).isInstanceOf(WeatherTimestamp.Observed.class);
        assertThat(retrieved).isInstanceOf(WeatherTimestamp.Retrieved.class);
        assertThat(observed.value()).isEqualTo(instant);
    }
}
