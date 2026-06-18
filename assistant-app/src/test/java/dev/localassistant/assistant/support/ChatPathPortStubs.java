package dev.localassistant.assistant.support;

import dev.localassistant.assistant.llm.LlmPort;
import dev.localassistant.assistant.rag.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.RagRetrievalResult;
import dev.localassistant.assistant.rag.domain.port.inbound.RetrieveRagKnowledge;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Registers stub {@link LlmPort} and {@link RetrieveRagKnowledge} singletons before context refresh so
 * the {@code @ConditionalOnBean} guard on {@code AnswerQuestionUseCase} (and the unconditional
 * {@code ChatController}) is satisfied in outbound-adapter integration contexts that boot the full
 * application but do not exercise the chat path.
 *
 * <p>Singletons are registered in the initializer (not via {@code @Import}) because
 * {@code @ConditionalOnBean} is evaluated against definitions present when the configuration class
 * is parsed; imported test beans register too late. A context that already declares a primary
 * {@link RetrieveRagKnowledge} keeps it: the stub is non-primary and is never chosen for injection.
 */
public final class ChatPathPortStubs
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        applicationContext.getBeanFactory().registerSingleton("stubChatPathLlmPort", stubLlmPort());
        applicationContext
                .getBeanFactory()
                .registerSingleton("stubChatPathRetrieveRagKnowledge", stubRetrieveRagKnowledge());
    }

    private static LlmPort stubLlmPort() {
        return (context, tokenSink) -> {
            throw notExercised();
        };
    }

    private static RetrieveRagKnowledge stubRetrieveRagKnowledge() {
        return command -> {
            throw notExercised();
        };
    }

    private static UnsupportedOperationException notExercised() {
        return new UnsupportedOperationException(
                "chat-path port stub is wiring-only and must not be invoked");
    }
}
