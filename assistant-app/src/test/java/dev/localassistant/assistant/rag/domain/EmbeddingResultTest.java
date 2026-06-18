package dev.localassistant.assistant.rag.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingResultTest {

    @Test
    void successDefensiveCopyPreventsMutation() {
        float[] embedding = validEmbedding();

        EmbeddingResult.Success success = new EmbeddingResult.Success(embedding);
        Arrays.fill(embedding, 99.0f);

        assertThat(success.embedding()[0]).isNotEqualTo(99.0f);
        assertThat(success.embedding()).isNotSameAs(embedding);
    }

    @Test
    void successRejectsWrongEmbeddingLength() {
        float[] embedding = new float[384];

        assertThatThrownBy(() -> new EmbeddingResult.Success(embedding))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("768");
    }

    @Test
    void successRejectsNullEmbedding() {
        assertThatThrownBy(() -> new EmbeddingResult.Success(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("embedding");
    }

    @Test
    void sourceUnavailableRejectsBlankSourceLabel() {
        assertThatThrownBy(() -> new EmbeddingResult.SourceUnavailable(" ", "message", "hint"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static float[] validEmbedding() {
        float[] embedding = new float[EmbeddingDimensions.VECTOR_SIZE];
        Arrays.fill(embedding, 0.1f);
        return embedding;
    }
}
