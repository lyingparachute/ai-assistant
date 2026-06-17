package dev.localassistant.assistant.question;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserQuestionTest {

    @Test
    void ofTrimsAndPreservesNonBlankText() {
        UserQuestion question = UserQuestion.of("  What is Berlin?  ");

        assertThat(question.text()).isEqualTo("What is Berlin?");
    }

    @Test
    void rejectsBlankText() {
        assertThatThrownBy(() -> UserQuestion.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsNullText() {
        assertThatThrownBy(() -> UserQuestion.of(null)).isInstanceOf(NullPointerException.class);
    }
}
