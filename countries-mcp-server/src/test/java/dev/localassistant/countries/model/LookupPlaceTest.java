package dev.localassistant.countries.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LookupPlaceTest {

    @Test
    void rejectsUnicodeWhitespaceOnlyInput() {
        assertThatThrownBy(() -> LookupPlace.of("\u2003"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void normalizesSurroundingAsciiWhitespace() {
        assertThat(LookupPlace.of(" Germany ").value()).isEqualTo("Germany");
    }
}
