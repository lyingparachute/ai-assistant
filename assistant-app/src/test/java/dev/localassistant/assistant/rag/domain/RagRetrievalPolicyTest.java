package dev.localassistant.assistant.rag.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagRetrievalPolicyTest {

    @Test
    void acceptsValidPolicy() {
        RagRetrievalPolicy policy = new RagRetrievalPolicy(5, 0.7);

        assertThat(policy.topK()).isEqualTo(5);
        assertThat(policy.relevanceThreshold()).isEqualTo(0.7);
    }

    @Test
    void rejectsZeroTopK() {
        assertThatThrownBy(() -> new RagRetrievalPolicy(0, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeTopK() {
        assertThatThrownBy(() -> new RagRetrievalPolicy(-1, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsThresholdBelowZero() {
        assertThatThrownBy(() -> new RagRetrievalPolicy(3, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsThresholdAboveOne() {
        assertThatThrownBy(() -> new RagRetrievalPolicy(3, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
