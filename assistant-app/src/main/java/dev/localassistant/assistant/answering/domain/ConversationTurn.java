package dev.localassistant.assistant.answering.domain;

import java.util.Objects;

public record ConversationTurn(UserQuestion question, AssistantAnswer answer) {

    public ConversationTurn {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(answer, "answer");
    }
}
