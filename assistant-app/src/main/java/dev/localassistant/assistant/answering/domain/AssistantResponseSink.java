package dev.localassistant.assistant.answering.domain;

public interface AssistantResponseSink {

    void recordSourceOutcome(SourceType type, SourceContributionStatus status);

    void appendAnswerToken(String delta);

    void complete(ConversationTurn turn);

    void failUnexpected(String errorCode, String message);

    static AssistantResponseSink discarding() {
        return new AssistantResponseSink() {
            @Override
            public void recordSourceOutcome(final SourceType type, final SourceContributionStatus status) {
            }

            @Override
            public void appendAnswerToken(final String delta) {
            }

            @Override
            public void complete(final ConversationTurn turn) {
            }

            @Override
            public void failUnexpected(final String errorCode, final String message) {
            }
        };
    }
}
