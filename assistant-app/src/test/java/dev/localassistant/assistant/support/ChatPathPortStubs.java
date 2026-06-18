package dev.localassistant.assistant.support;

import dev.localassistant.assistant.rag.domain.port.inbound.RetrieveRagKnowledge;
import dev.localassistant.assistant.synthesis.domain.port.outbound.LlmPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Registers stub {@link LlmPort} and {@link RetrieveRagKnowledge} singletons before context refresh so
 * outbound-adapter integration contexts that boot the full application but do not exercise the chat path
 * still satisfy {@link dev.localassistant.assistant.answering.api.http.ChatController} wiring.
 *
 * <p>Singletons are registered in the initializer (not via {@code @Import}) because imported test beans
 * register too late for {@code @ConditionalOnMissingBean} on the production use-case beans. A context that
 * already declares a primary {@link RetrieveRagKnowledge} keeps it: the stub is non-primary and is never
 * chosen for injection.
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
