package dev.localassistant.assistant;

import dev.localassistant.assistant.answering.api.http.ChatController;
import dev.localassistant.assistant.answering.domain.port.inbound.AnswerQuestion;
import dev.localassistant.assistant.shared.mcp.McpToolInvoker;
import dev.localassistant.assistant.support.ChatPathPortStubs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(
        initializers = {ChatPathPortStubs.class, AssistantContextLoadTest.StubMcpToolInvoker.class})
class AssistantContextLoadTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void defaultContextBootsWithChatPathWired() {
        assertThat(applicationContext.getBeanNamesForType(ChatController.class)).hasSize(1);
        assertThat(applicationContext.getBeanNamesForType(AnswerQuestion.class)).hasSize(1);
    }

    @Test
    void exposesExactlyOneProductionClockBean() {
        String[] clockBeanNames = applicationContext.getBeanNamesForType(Clock.class);

        assertThat(clockBeanNames).containsExactly("systemClock");
    }

    static class StubMcpToolInvoker
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            applicationContext.getBeanFactory().registerSingleton("stubMcpToolInvoker", invoker());
        }

        private static McpToolInvoker invoker() {
            return (String toolName, Map<String, Object> arguments) -> {
                throw new UnsupportedOperationException(
                        "context-load smoke test does not exercise MCP tool behavior");
            };
        }
    }
}
