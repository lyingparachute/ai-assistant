package dev.localassistant.assistant.adapters.outbound.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.assistant.config.AssistantMcpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class StdioMcpToolInvokerConstructionTest {

    @Test
    void springResolvesExactlyOneAutowireConstructor() {
        AutowiredAnnotationBeanPostProcessor processor = new AutowiredAnnotationBeanPostProcessor();

        Constructor<?>[] candidates =
                processor.determineCandidateConstructors(StdioMcpToolInvoker.class, "stdioMcpToolInvoker");

        assertThat(candidates).as("Spring must pick a single autowire constructor").isNotNull();
        assertThat(candidates).hasSize(1);
        assertThat(candidates[0].getParameterTypes())
                .containsExactly(AssistantMcpProperties.class, ObjectMapper.class);
    }
}
