package dev.localassistant.assistant.answering.domain.support;

import dev.localassistant.assistant.rag.domain.RagRetrievalResult;
import dev.localassistant.assistant.rag.domain.port.inbound.RetrieveRagKnowledge;

import java.util.Objects;
import java.util.function.Function;

public final class StubRetrieveRagKnowledge implements RetrieveRagKnowledge {

    private Function<RetrieveRagKnowledge.Command, RagRetrievalResult> retrieveHandler =
            command -> new RagRetrievalResult.NoRelevantKnowledge();
    private int retrieveInvocationCount;

    public StubRetrieveRagKnowledge onRetrieve(
            Function<RetrieveRagKnowledge.Command, RagRetrievalResult> handler) {
        this.retrieveHandler = Objects.requireNonNull(handler);
        return this;
    }

    @Override
    public RagRetrievalResult execute(RetrieveRagKnowledge.Command command) {
        retrieveInvocationCount++;
        return retrieveHandler.apply(command);
    }

    public int retrieveInvocationCount() {
        return retrieveInvocationCount;
    }
}
