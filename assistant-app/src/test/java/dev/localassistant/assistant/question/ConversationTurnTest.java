package dev.localassistant.assistant.question;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationTurnTest {

    @Test
    void composesQuestionAndAnswer() {
        UserQuestion question = UserQuestion.of("What is the capital city of Germany?");
        AssistantAnswer answer = AssistantAnswer.of("Berlin.", List.of());

        ConversationTurn turn = new ConversationTurn(question, answer);

        assertThat(turn.question()).isEqualTo(question);
        assertThat(turn.answer()).isEqualTo(answer);
    }

    @Test
    void rejectsNullQuestion() {
        assertThatThrownBy(() -> new ConversationTurn(null, AssistantAnswer.of("answer", List.of())))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("question");
    }

    @Test
    void rejectsNullAnswer() {
        assertThatThrownBy(
                        () ->
                                new ConversationTurn(
                                        UserQuestion.of("What is the capital city of Germany?"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("answer");
    }
}
