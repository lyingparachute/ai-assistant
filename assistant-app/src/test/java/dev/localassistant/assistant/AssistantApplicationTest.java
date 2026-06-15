package dev.localassistant.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantApplicationTest {

    @Test
    void exposesSpringBootApplicationEntrypoint() {
        assertThat(AssistantApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .isTrue();
    }
}
