package dev.localassistant.assistant.countryfacts.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountryInfoTest {

    @Test
    void rejectsBlankFields() {
        assertThatThrownBy(() -> new CountryInfo(" ", "Berlin", "Europe", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
