package dev.localassistant.assistant.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalScoreTest {

    @Test
    void acceptsScoreWithinUnitRange() {
        assertThat(new RetrievalScore(0.0).value()).isZero();
        assertThat(new RetrievalScore(1.0).value()).isEqualTo(1.0);
        assertThat(new RetrievalScore(0.42).value()).isEqualTo(0.42);
    }

    @Test
    void rejectsScoreBelowZero() {
        assertThatThrownBy(() -> new RetrievalScore(-0.01))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsScoreAboveOne() {
        assertThatThrownBy(() -> new RetrievalScore(1.01))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
