package dev.localassistant.assistant.rag.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingWindowTest {

    @Test
    void rejectsOverlapGreaterThanOrEqualToMaxSize() {
        assertThatThrownBy(() -> ChunkingWindow.of(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeOverlap() {
        assertThatThrownBy(() -> ChunkingWindow.of(100, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveMaxSize() {
        assertThatThrownBy(() -> ChunkingWindow.of(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
