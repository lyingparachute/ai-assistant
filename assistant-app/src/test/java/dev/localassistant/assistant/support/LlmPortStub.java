package dev.localassistant.assistant.support;

import dev.localassistant.assistant.synthesis.domain.port.outbound.LlmPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Registers a stub {@link LlmPort} singleton before context refresh so {@code AnswerQuestion}
 * and {@code ChatController} can load in integration contexts that do not exercise the chat path.
 */
public final class LlmPortStub
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        applicationContext.getBeanFactory().registerSingleton("stubChatPathLlmPort", stubLlmPort());
    }

    private static LlmPort stubLlmPort() {
        return (context, tokenSink) -> {
            throw new UnsupportedOperationException(
                    "chat-path LLM port stub is wiring-only and must not be invoked");
        };
    }
}
