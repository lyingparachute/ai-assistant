package dev.localassistant.assistant.question;

import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.Location;
import dev.localassistant.assistant.tools.Temperature;
import dev.localassistant.assistant.tools.WeatherReport;
import dev.localassistant.assistant.tools.WeatherTimestamp;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerSourceTest {

    private static final CountryInfo GERMANY =
            new CountryInfo("Germany", "Berlin", "Europe", 83_000_000L);
    private static final WeatherReport MUNICH_WEATHER =
            new WeatherReport(
                    new Location("Munich"),
                    Temperature.celsius(12.5),
                    new WeatherTimestamp.Retrieved(Instant.parse("2026-06-16T10:00:00Z")));
    private static final KnowledgeSnippet SNIPPET =
            KnowledgeSnippet.fromStoredChunk(
                    "Fraud Guard detects anomalies.", "https://www.cdq.com/products/cdq-fraud-guard", "hash", 0);

    @Test
    void countriesFactsUsedExposesCountryInfo() {
        AnswerSource.CountriesFacts source = AnswerSource.CountriesFacts.used(GERMANY);

        assertThat(source.status()).isEqualTo(SourceContributionStatus.USED);
        assertThat(source.countryInfo()).contains(GERMANY);
    }

    @Test
    void countriesFactsUnavailableRequiresMessageAndHint() {
        AnswerSource.CountriesFacts source =
                AnswerSource.CountriesFacts.unavailable("countries down", "retry later");

        assertThat(source.status()).isEqualTo(SourceContributionStatus.UNAVAILABLE);
        assertThat(source.countryInfo()).isEmpty();
        assertThat(source.unavailableMessage()).isEqualTo("countries down");
    }

    @Test
    void countriesFactsRejectsInsufficientStatus() {
        assertThatThrownBy(
                        () ->
                                new AnswerSource.CountriesFacts(
                                        SourceContributionStatus.INSUFFICIENT,
                                        null,
                                        false,
                                        "",
                                        ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weatherObservationUsedExposesWeatherReport() {
        AnswerSource.WeatherObservation source = AnswerSource.WeatherObservation.used(MUNICH_WEATHER);

        assertThat(source.status()).isEqualTo(SourceContributionStatus.USED);
        assertThat(source.weatherReport()).contains(MUNICH_WEATHER);
    }

    @Test
    void weatherObservationUnavailableRequiresMessageAndHint() {
        AnswerSource.WeatherObservation source =
                AnswerSource.WeatherObservation.unavailable("weather down", "retry later");

        assertThat(source.status()).isEqualTo(SourceContributionStatus.UNAVAILABLE);
        assertThat(source.weatherReport()).isEmpty();
    }

    @Test
    void ragKnowledgeUsedRequiresSnippets() {
        AnswerSource.RagKnowledge source = AnswerSource.RagKnowledge.used(List.of(SNIPPET));

        assertThat(source.status()).isEqualTo(SourceContributionStatus.USED);
        assertThat(source.snippets()).containsExactly(SNIPPET);
    }

    @Test
    void ragKnowledgeInsufficientHasNoSnippets() {
        AnswerSource.RagKnowledge source = AnswerSource.RagKnowledge.insufficient();

        assertThat(source.status()).isEqualTo(SourceContributionStatus.INSUFFICIENT);
        assertThat(source.snippets()).isEmpty();
    }

    @Test
    void ragKnowledgeUnavailableRequiresMessageAndHint() {
        AnswerSource.RagKnowledge source =
                AnswerSource.RagKnowledge.unavailable("rag down", "retry later");

        assertThat(source.status()).isEqualTo(SourceContributionStatus.UNAVAILABLE);
        assertThat(source.snippets()).isEmpty();
    }

    @Test
    void modelSynthesisUsedHasNoUnavailableDetails() {
        AnswerSource.ModelSynthesis source = AnswerSource.ModelSynthesis.used();

        assertThat(source.status()).isEqualTo(SourceContributionStatus.USED);
        assertThat(source.unavailableMessage()).isEmpty();
    }

    @Test
    void modelSynthesisUnavailableRequiresMessageAndHint() {
        AnswerSource.ModelSynthesis source =
                AnswerSource.ModelSynthesis.unavailable("ollama down", "start ollama");

        assertThat(source.status()).isEqualTo(SourceContributionStatus.UNAVAILABLE);
        assertThat(source.unavailableMessage()).isEqualTo("ollama down");
    }
}
