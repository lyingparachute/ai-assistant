package dev.localassistant.assistant.rag.domain.port.inbound;

import dev.localassistant.assistant.rag.domain.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.domain.RagRetrievalResult;

import java.util.Objects;

public interface RetrieveRagKnowledge {

    RagRetrievalResult execute(Command command);

    record Command(String questionText, RagRetrievalPolicy policy) {

        public Command {
            if (questionText == null || questionText.isBlank()) {
                throw new IllegalArgumentException("questionText must not be blank");
            }
            Objects.requireNonNull(policy, "policy");
        }
    }
}
