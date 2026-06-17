package dev.localassistant.assistant.orchestration.support;

import dev.localassistant.assistant.rag.RagChunk;
import dev.localassistant.assistant.rag.RagIngestionResult;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import dev.localassistant.assistant.rag.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.RagRetrievalResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

public final class StubRagKnowledgePort implements RagKnowledgePort {

    private BiFunction<String, RagRetrievalPolicy, RagRetrievalResult> retrieveHandler =
            (question, policy) -> new RagRetrievalResult.NoRelevantKnowledge();
    private int retrieveInvocationCount;

    public StubRagKnowledgePort onRetrieve(BiFunction<String, RagRetrievalPolicy, RagRetrievalResult> handler) {
        this.retrieveHandler = Objects.requireNonNull(handler);
        return this;
    }

    @Override
    public RagRetrievalResult retrieve(String questionText, RagRetrievalPolicy policy) {
        retrieveInvocationCount++;
        return retrieveHandler.apply(questionText, policy);
    }

    @Override
    public RagIngestionResult storeChunks(String sourceUrl, String contentHash, List<RagChunk> chunks) {
        throw new UnsupportedOperationException("not used in orchestration tests");
    }

    @Override
    public Optional<String> findContentHashForSource(String sourceUrl) {
        return Optional.empty();
    }

    @Override
    public int countChunksForSource(String sourceUrl) {
        return 0;
    }

    public int retrieveInvocationCount() {
        return retrieveInvocationCount;
    }
}
