package dev.localassistant.assistant.answering.domain.port.inbound;

import dev.localassistant.assistant.answering.domain.AssistantResponseSink;
import dev.localassistant.assistant.answering.domain.ConversationTurn;
import dev.localassistant.assistant.answering.domain.UserQuestion;

import java.util.Objects;

public interface AnswerQuestion {

    ConversationTurn execute(Command command);

    record Command(UserQuestion question, AssistantResponseSink sink) {

        public Command {
            Objects.requireNonNull(question, "question");
            Objects.requireNonNull(sink, "sink");
        }
    }
}
