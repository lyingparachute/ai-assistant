package dev.localassistant.assistant.orchestration;

import dev.localassistant.assistant.llm.LlmResult;
import dev.localassistant.assistant.question.AnswerSource;
import dev.localassistant.assistant.question.AssistantAnswer;
import dev.localassistant.assistant.question.SourceContributionStatus;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.Location;
import dev.localassistant.assistant.tools.SourceUnavailability;
import dev.localassistant.assistant.tools.Temperature;
import dev.localassistant.assistant.tools.WeatherReport;
import dev.localassistant.assistant.tools.WeatherTimestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseComposerTest {

    private static final String TRACE_ID = "trace-123";
    private static final CountryInfo GERMANY =
            new CountryInfo("Germany", "Berlin", "Europe", 83_240_525L);
    private static final WeatherReport MUNICH_WEATHER =
            new WeatherReport(
                    Location.of("Munich"),
                    Temperature.celsius(18.3),
                    new WeatherTimestamp.Retrieved(Instant.parse("2026-06-16T12:00:00Z")));
    private static final WeatherReport BERLIN_WEATHER =
            new WeatherReport(
                    Location.of("Berlin"),
                    Temperature.celsius(15.0),
                    new WeatherTimestamp.Observed(Instant.parse("2026-06-16T11:30:00Z")));
    private static final KnowledgeSnippet SNIPPET =
            KnowledgeSnippet.fromRetrieval(
                    "Fraud Guard detects anomalies.",
                    "https://www.cdq.com/products/cdq-fraud-guard",
                    "hash",
                    0,
                    0.82);

    private ResponseComposer composer;

    @BeforeEach
    void setUp() {
        composer = new ResponseComposer();
    }

    @Test
    void countriesOnlyAnswerLabelsCountryFactsUsed() {
        AssistantAnswer answer = composer.composeCountryCapital(GERMANY, TRACE_ID);

        assertThat(answer.answerText()).isEqualTo("The capital of Germany is Berlin.");
        assertThat(answer.sources()).hasSize(1);
        assertThat(answer.sources().getFirst()).isInstanceOf(AnswerSource.CountriesFacts.class);
        assertThat(((AnswerSource.CountriesFacts) answer.sources().getFirst()).status())
                .isEqualTo(SourceContributionStatus.USED);
        assertThat(answer.traceCorrelationId()).contains(TRACE_ID);
    }

    @Test
    void weatherOnlyAnswerLabelsRetrievedTimestamp() {
        AssistantAnswer answer = composer.composeWeatherOnly(MUNICH_WEATHER, TRACE_ID);

        assertThat(answer.answerText()).contains("Munich").contains("18.3°C").contains("Retrieved:");
        assertThat(answer.sources()).singleElement().isInstanceOf(AnswerSource.WeatherObservation.class);
        assertThat(((AnswerSource.WeatherObservation) answer.sources().getFirst()).status())
                .isEqualTo(SourceContributionStatus.USED);
    }

    @Test
    void combinedAnswerIncludesBothCountryAndWeatherSources() {
        AssistantAnswer answer = composer.composeCountryThenWeather(GERMANY, BERLIN_WEATHER, TRACE_ID);

        assertThat(answer.answerText()).contains("Berlin").contains("Germany").contains("Observed:");
        assertThat(answer.sources()).hasSize(2);
        assertThat(answer.sources().get(0)).isInstanceOf(AnswerSource.CountriesFacts.class);
        assertThat(answer.sources().get(1)).isInstanceOf(AnswerSource.WeatherObservation.class);
    }

    @Test
    void partialCombinedAnswerKeepsCountryFactsAndMarksWeatherUnavailable() {
        SourceUnavailability weatherFailure =
                new SourceUnavailability("weather MCP", "weather service down", "retry later");

        AssistantAnswer answer =
                composer.composeCountryThenWeatherPartial(GERMANY, weatherFailure, TRACE_ID);

        assertThat(answer.answerText()).contains("capital of Germany is Berlin").contains("unavailable");
        assertThat(answer.sources()).hasSize(2);
        assertThat(((AnswerSource.CountriesFacts) answer.sources().get(0)).status())
                .isEqualTo(SourceContributionStatus.USED);
        assertThat(((AnswerSource.WeatherObservation) answer.sources().get(1)).status())
                .isEqualTo(SourceContributionStatus.UNAVAILABLE);
    }

    @Test
    void placeSynthesisDistinguishesCountryFactsFromModelSynthesis() {
        AssistantAnswer answer =
                composer.composePlaceSynthesis(
                        GERMANY, new LlmResult.Success("It is a major European city."), TRACE_ID);

        assertThat(answer.answerText()).contains("Berlin is the capital of Germany");
        assertThat(answer.sources()).hasSize(2);
        assertThat(answer.sources().get(0)).isInstanceOf(AnswerSource.CountriesFacts.class);
        assertThat(answer.sources().get(1)).isInstanceOf(AnswerSource.ModelSynthesis.class);
        assertThat(((AnswerSource.ModelSynthesis) answer.sources().get(1)).status())
                .isEqualTo(SourceContributionStatus.USED);
    }

    @Test
    void placeSynthesisLlmUnavailableDoesNotLabelSynthesisAsToolResult() {
        AssistantAnswer answer =
                composer.composePlaceSynthesisLlmUnavailable(
                        GERMANY,
                        new SourceUnavailability("Ollama chat", "model offline", "start Ollama"),
                        TRACE_ID);

        assertThat(answer.answerText()).contains("Berlin is the capital of Germany");
        assertThat(answer.sources()).hasSize(2);
        assertThat(answer.sources().get(0)).isInstanceOf(AnswerSource.CountriesFacts.class);
        assertThat(answer.sources().get(1)).isInstanceOf(AnswerSource.ModelSynthesis.class);
        assertThat(answer.sources().get(1)).isNotInstanceOf(AnswerSource.WeatherObservation.class);
    }

    @Test
    void cdqAnswerLabelsRagKnowledgeAndModelSynthesisSeparately() {
        AssistantAnswer answer =
                composer.composeCdqProduct(
                        List.of(SNIPPET),
                        new LlmResult.Success("Fraud Guard helps detect fraud."),
                        TRACE_ID);

        assertThat(answer.sources()).hasSize(2);
        assertThat(answer.sources().get(0)).isInstanceOf(AnswerSource.RagKnowledge.class);
        assertThat(answer.sources().get(1)).isInstanceOf(AnswerSource.ModelSynthesis.class);
    }

    @Test
    void cdqInsufficientKnowledgeUsesRagInsufficientStatus() {
        AssistantAnswer answer = composer.composeCdqInsufficientKnowledge(TRACE_ID);

        assertThat(answer.answerText()).contains("insufficient product knowledge");
        assertThat(answer.sources()).singleElement().isInstanceOf(AnswerSource.RagKnowledge.class);
        assertThat(((AnswerSource.RagKnowledge) answer.sources().getFirst()).status())
                .isEqualTo(SourceContributionStatus.INSUFFICIENT);
    }

    @Test
    void weatherUnavailableLabelsWeatherObservationUnavailable() {
        SourceUnavailability weatherFailure =
                new SourceUnavailability("weather MCP", "weather service down", "retry later");

        AssistantAnswer answer = composer.composeWeatherUnavailable(weatherFailure, TRACE_ID);

        assertThat(answer.answerText()).contains("Weather MCP is unavailable");
        assertThat(((AnswerSource.WeatherObservation) answer.sources().getFirst()).status())
                .isEqualTo(SourceContributionStatus.UNAVAILABLE);
    }

    @Test
    void cdqLlmUnavailableLabelsModelSynthesisUnavailable() {
        AssistantAnswer answer =
                composer.composeCdqLlmUnavailable(
                        List.of(SNIPPET),
                        new SourceUnavailability("Ollama chat", "model offline", "start Ollama"),
                        TRACE_ID);

        assertThat(answer.answerText()).contains("Ollama chat is unavailable");
        assertThat(answer.sources()).hasSize(2);
        assertThat(((AnswerSource.RagKnowledge) answer.sources().get(0)).status())
                .isEqualTo(SourceContributionStatus.USED);
        assertThat(((AnswerSource.ModelSynthesis) answer.sources().get(1)).status())
                .isEqualTo(SourceContributionStatus.UNAVAILABLE);
    }

    @Test
    void cdqRagUnavailableUsesRagUnavailableStatus() {
        AssistantAnswer answer =
                composer.composeCdqRagUnavailable(
                        new SourceUnavailability("pgvector RAG", "database down", "start postgres"),
                        TRACE_ID);

        assertThat(answer.answerText()).contains("RAG knowledge is unavailable");
        assertThat(((AnswerSource.RagKnowledge) answer.sources().getFirst()).status())
                .isEqualTo(SourceContributionStatus.UNAVAILABLE);
    }
}
