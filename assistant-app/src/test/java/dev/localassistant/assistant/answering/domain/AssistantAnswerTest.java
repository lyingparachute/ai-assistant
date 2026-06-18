package dev.localassistant.assistant.answering.domain;

import dev.localassistant.assistant.countryfacts.domain.CountryInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantAnswerTest {

    @Test
    void sourcesListIsDefensivelyCopied() {
        AnswerSource.CountriesFacts source =
                AnswerSource.CountriesFacts.used(
                        new CountryInfo("Germany", "Berlin", "Europe", 83_000_000L));
        List<AnswerSource> sources = new ArrayList<>(List.of(source));

        AssistantAnswer answer = AssistantAnswer.of("Berlin is the capital.", sources);
        sources.add(AnswerSource.ModelSynthesis.used());

        assertThat(answer.sources()).containsExactly(source);
    }

    @Test
    void ofReturnsImmutableSourcesWithoutTrace() {
        AnswerSource.CountriesFacts source =
                AnswerSource.CountriesFacts.used(
                        new CountryInfo("Germany", "Berlin", "Europe", 83_000_000L));

        AssistantAnswer answer = AssistantAnswer.of("Berlin is the capital.", List.of(source));

        assertThat(answer.answerText()).isEqualTo("Berlin is the capital.");
        assertThat(answer.sources()).containsExactly(source);
        assertThat(answer.traceCorrelationId()).isEmpty();
    }

    @Test
    void withTraceExposesCorrelationId() {
        AssistantAnswer answer =
                AssistantAnswer.withTrace("answer", List.of(), "trace-123");

        assertThat(answer.traceCorrelationId()).contains("trace-123");
    }

    @Test
    void rejectsBlankAnswerText() {
        assertThatThrownBy(() -> AssistantAnswer.of(" ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankTraceCorrelationId() {
        assertThatThrownBy(() -> AssistantAnswer.withTrace("answer", List.of(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
