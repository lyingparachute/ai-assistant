package dev.localassistant.assistant.synthesis.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptContextTest {

    @Test
    void defensiveCopyPreventsMutationOfGroundedFacts() {
        List<String> facts = new ArrayList<>();
        facts.add("Berlin is the capital of Germany.");

        PromptContext context =
                new PromptContext("What do you know about Berlin?", facts, "Answer from facts only.");
        facts.add("mutated");

        assertThat(context.groundedFacts()).containsExactly("Berlin is the capital of Germany.");
    }

    @Test
    void rejectsBlankUserQuestionText() {
        assertThatThrownBy(
                        () -> new PromptContext(" ", List.of(), "Answer from facts only."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userQuestionText");
    }

    @Test
    void rejectsBlankSynthesisInstructions() {
        assertThatThrownBy(
                        () -> new PromptContext("What is CDQ Fraud Guard?", List.of(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("synthesisInstructions");
    }

    @Test
    void rejectsBlankGroundedFactEntry() {
        assertThatThrownBy(
                        () ->
                                new PromptContext(
                                        "What is CDQ Fraud Guard?",
                                        List.of("valid", " "),
                                        "Summarize."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groundedFacts");
    }

    @Test
    void rejectsNullGroundedFacts() {
        assertThatThrownBy(
                        () -> new PromptContext("What is CDQ Fraud Guard?", null, "Summarize."))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("groundedFacts");
    }
}
