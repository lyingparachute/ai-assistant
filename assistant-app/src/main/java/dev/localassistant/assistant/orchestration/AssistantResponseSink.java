package dev.localassistant.assistant.orchestration;

import dev.localassistant.assistant.question.ConversationTurn;
import dev.localassistant.assistant.question.SourceContributionStatus;

public interface AssistantResponseSink {

    void recordSourceOutcome(SourceType type, SourceContributionStatus status);

    void appendAnswerToken(String delta);

    void complete(ConversationTurn turn);

    void failUnexpected(String errorCode, String message);

    static AssistantResponseSink discarding() {
        return new AssistantResponseSink() {
            @Override
            public void recordSourceOutcome(SourceType type, SourceContributionStatus status) {}

            @Override
            public void appendAnswerToken(String delta) {}

            @Override
            public void complete(ConversationTurn turn) {}

            @Override
            public void failUnexpected(String errorCode, String message) {}
        };
    }
}
