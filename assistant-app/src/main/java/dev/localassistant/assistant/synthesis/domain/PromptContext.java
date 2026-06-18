package dev.localassistant.assistant.synthesis.domain;

import java.util.List;
import java.util.Objects;

public record PromptContext(
        String userQuestionText, List<String> groundedFacts, String synthesisInstructions) {

    public PromptContext {
        Objects.requireNonNull(userQuestionText, "userQuestionText");
        Objects.requireNonNull(groundedFacts, "groundedFacts");
        Objects.requireNonNull(synthesisInstructions, "synthesisInstructions");
        if (userQuestionText.isBlank()) {
            throw new IllegalArgumentException("userQuestionText must not be blank");
        }
        if (synthesisInstructions.isBlank()) {
            throw new IllegalArgumentException("synthesisInstructions must not be blank");
        }
        groundedFacts = List.copyOf(groundedFacts);
        for (String fact : groundedFacts) {
            Objects.requireNonNull(fact, "groundedFact");
            if (fact.isBlank()) {
                throw new IllegalArgumentException("groundedFacts must not contain blank entries");
            }
        }
    }
}
