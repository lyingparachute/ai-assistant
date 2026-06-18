package dev.localassistant.assistant.answering.domain;

import dev.localassistant.assistant.countryfacts.domain.CountryInfo;
import dev.localassistant.assistant.rag.domain.KnowledgeSnippet;
import dev.localassistant.assistant.shared.SourceUnavailability;
import dev.localassistant.assistant.weather.domain.Location;
import dev.localassistant.assistant.weather.domain.Temperature;
import dev.localassistant.assistant.weather.domain.WeatherReport;
import dev.localassistant.assistant.weather.domain.WeatherTimestamp;
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
            KnowledgeSnippet.fromRetrieval(
                    "Fraud Guard detects anomalies.",
                    "https://www.cdq.com/products/cdq-fraud-guard",
                    "hash",
                    0,
                    0.82);
    private static final SourceUnavailability COUNTRIES_DOWN =
            new SourceUnavailability("Countries MCP", "countries down", "retry later");
    private static final SourceUnavailability WEATHER_DOWN =
            new SourceUnavailability("Weather MCP", "weather down", "retry later");
    private static final SourceUnavailability RAG_DOWN =
            new SourceUnavailability("RAG knowledge", "rag down", "retry later");
    private static final SourceUnavailability OLLAMA_DOWN =
            new SourceUnavailability("Ollama chat", "ollama down", "start ollama");

    @Test
    void countriesFactsUsedExposesCountryInfo() {
        AnswerSource.CountriesFacts source = AnswerSource.CountriesFacts.used(GERMANY);

        assertThat(source).isInstanceOf(AnswerSource.CountriesFacts.Used.class);
        assertThat(source.status()).isEqualTo(SourceContributionStatus.USED);
        assertThat(source.countryInfo()).contains(GERMANY);
    }

    @Test
    void countriesFactsUnavailableCarriesUnavailability() {
        AnswerSource.CountriesFacts source = AnswerSource.CountriesFacts.unavailable(COUNTRIES_DOWN);

        assertThat(source).isEqualTo(new AnswerSource.CountriesFacts.Unavailable(COUNTRIES_DOWN));
        assertThat(source.status()).isEqualTo(SourceContributionStatus.UNAVAILABLE);
        assertThat(source.countryInfo()).isEmpty();
    }

    @Test
    void countriesFactsUnavailableRejectsMissingUnavailability() {
        assertThatThrownBy(() -> AnswerSource.CountriesFacts.unavailable(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void weatherObservationUsedExposesWeatherReport() {
        AnswerSource.WeatherObservation source = AnswerSource.WeatherObservation.used(MUNICH_WEATHER);

        assertThat(source.status()).isEqualTo(SourceContributionStatus.USED);
        assertThat(source.weatherReport()).contains(MUNICH_WEATHER);
    }

    @Test
    void weatherObservationUnavailableCarriesUnavailability() {
        AnswerSource.WeatherObservation source = AnswerSource.WeatherObservation.unavailable(WEATHER_DOWN);

        assertThat(source).isEqualTo(new AnswerSource.WeatherObservation.Unavailable(WEATHER_DOWN));
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
    void ragKnowledgeUsedRejectsEmptySnippets() {
        assertThatThrownBy(() -> AnswerSource.RagKnowledge.used(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ragKnowledgeInsufficientHasNoSnippets() {
        AnswerSource.RagKnowledge source = AnswerSource.RagKnowledge.insufficient();

        assertThat(source.status()).isEqualTo(SourceContributionStatus.INSUFFICIENT);
        assertThat(source.snippets()).isEmpty();
    }

    @Test
    void ragKnowledgeUnavailableCarriesUnavailability() {
        AnswerSource.RagKnowledge source = AnswerSource.RagKnowledge.unavailable(RAG_DOWN);

        assertThat(source).isEqualTo(new AnswerSource.RagKnowledge.Unavailable(RAG_DOWN));
        assertThat(source.status()).isEqualTo(SourceContributionStatus.UNAVAILABLE);
        assertThat(source.snippets()).isEmpty();
    }

    @Test
    void modelSynthesisUsedHasStatusUsed() {
        AnswerSource.ModelSynthesis source = AnswerSource.ModelSynthesis.used();

        assertThat(source).isInstanceOf(AnswerSource.ModelSynthesis.Used.class);
        assertThat(source.status()).isEqualTo(SourceContributionStatus.USED);
    }

    @Test
    void modelSynthesisUnavailableCarriesUnavailability() {
        AnswerSource.ModelSynthesis source = AnswerSource.ModelSynthesis.unavailable(OLLAMA_DOWN);

        assertThat(source).isEqualTo(new AnswerSource.ModelSynthesis.Unavailable(OLLAMA_DOWN));
        assertThat(source.status()).isEqualTo(SourceContributionStatus.UNAVAILABLE);
    }

    // The sealed Used/Unavailable variants make invalid states unrepresentable by construction.
    // A `Used` variant carries only its resolved payload; it has no unavailability field, so the
    // following lines do not compile (kept as documented evidence, not active code):
    //   new AnswerSource.CountriesFacts.Used(GERMANY, COUNTRIES_DOWN);  // too many arguments
    //   AnswerSource.CountriesFacts.Used used = AnswerSource.CountriesFacts.unavailable(COUNTRIES_DOWN); // wrong type
}
