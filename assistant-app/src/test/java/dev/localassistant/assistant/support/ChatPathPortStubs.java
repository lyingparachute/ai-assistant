package dev.localassistant.assistant.support;

import dev.localassistant.assistant.llm.LlmPort;
import dev.localassistant.assistant.rag.ChunkStorageOutcome;
import dev.localassistant.assistant.rag.RagChunk;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import dev.localassistant.assistant.rag.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.RagRetrievalResult;
import dev.localassistant.assistant.rag.StoredSourceState;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

/**
 * Registers stub {@link LlmPort} and {@link RagKnowledgePort} singletons before context refresh so
 * the {@code @ConditionalOnBean} guard on {@code AnswerQuestionUseCase} (and the unconditional
 * {@code ChatController}) is satisfied in outbound-adapter integration contexts that boot the full
 * application but do not exercise the chat path.
 *
 * <p>Singletons are registered in the initializer (not via {@code @Import}) because
 * {@code @ConditionalOnBean} is evaluated against definitions present when the configuration class
 * is parsed; imported test beans register too late. A context that already declares a primary
 * {@link RagKnowledgePort} keeps it: the stub is non-primary and is never chosen for injection.
 */
public final class ChatPathPortStubs
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        applicationContext.getBeanFactory().registerSingleton("stubChatPathLlmPort", stubLlmPort());
        applicationContext
                .getBeanFactory()
                .registerSingleton("stubChatPathRagKnowledgePort", stubRagKnowledgePort());
    }

    private static LlmPort stubLlmPort() {
        return (context, tokenSink) -> {
            throw notExercised();
        };
    }

    private static RagKnowledgePort stubRagKnowledgePort() {
        return new RagKnowledgePort() {
            @Override
            public RagRetrievalResult retrieve(String questionText, RagRetrievalPolicy policy) {
                throw notExercised();
            }

            @Override
            public ChunkStorageOutcome storeChunks(
                    String sourceUrl, String contentHash, List<RagChunk> chunks) {
                throw notExercised();
            }

            @Override
            public StoredSourceState findContentHashForSource(String sourceUrl) {
                throw notExercised();
            }
        };
    }

    private static UnsupportedOperationException notExercised() {
        return new UnsupportedOperationException(
                "chat-path port stub is wiring-only and must not be invoked");
    }
}
