package dev.localassistant.assistant.answering.domain;

import dev.localassistant.assistant.countryfacts.domain.CountryInfo;
import dev.localassistant.assistant.rag.domain.KnowledgeSnippet;
import dev.localassistant.assistant.shared.SourceUnavailability;
import dev.localassistant.assistant.synthesis.domain.LlmResult;
import dev.localassistant.assistant.weather.domain.Temperature;
import dev.localassistant.assistant.weather.domain.WeatherReport;
import dev.localassistant.assistant.weather.domain.WeatherTimestamp;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

final class ResponseComposer {

    static final String COUNTRIES_SOURCE_LABEL = "Countries MCP";
    static final String WEATHER_SOURCE_LABEL = "Weather MCP";
    static final String RAG_SOURCE_LABEL = "RAG knowledge";
    static final String OLLAMA_SOURCE_LABEL = "Ollama chat";

    static final String CAPITAL_FACT_TEMPLATE = "%s is the capital of %s.";

    private static final String CAPITAL_ANSWER_TEMPLATE = "The capital of %s is %s.";
    private static final String WEATHER_ANSWER_TEMPLATE =
        "The current temperature in %s is %s. %s: %s";
    private static final String COMBINED_WEATHER_TEMPLATE =
        "The current temperature in %s, the capital of %s, is %s. %s: %s";
    private static final String WEATHER_UNAVAILABLE_AFTER_COUNTRY_TEMPLATE =
        "The capital of %s is %s. Weather for %s is unavailable: %s";
    private static final String INSUFFICIENT_PRODUCT_KNOWLEDGE_MESSAGE =
        "I have insufficient product knowledge to answer this CDQ Fraud Guard question.";
    private static final String UNSUPPORTED_QUESTION_TEMPLATE =
        "I cannot answer this question: %s";

    private static final String TIMESTAMP_LABEL_OBSERVED = "Observed";
    private static final String TIMESTAMP_LABEL_RETRIEVED = "Retrieved";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ISO_INSTANT.withLocale(Locale.ROOT);

    AssistantAnswer composeCountryCapital(final CountryInfo countryInfo, final String traceCorrelationId) {
        final var answerText =
            CAPITAL_ANSWER_TEMPLATE.formatted(countryInfo.countryName(), countryInfo.capital());
        return withTrace(
            answerText,
            List.of(AnswerSource.CountriesFacts.used(countryInfo)),
            traceCorrelationId);
    }

    AssistantAnswer composeCountriesUnavailable(
        final SourceUnavailability failure, final String traceCorrelationId) {
        final var message = formatSourceUnavailableMessage(COUNTRIES_SOURCE_LABEL, failure.message());
        return withTrace(
            message,
            List.of(AnswerSource.CountriesFacts.unavailable(failure)),
            traceCorrelationId);
    }

    AssistantAnswer composeWeatherOnly(final WeatherReport weatherReport, final String traceCorrelationId) {
        final var answerText = formatWeatherAnswer(weatherReport);
        return withTrace(
            answerText,
            List.of(AnswerSource.WeatherObservation.used(weatherReport)),
            traceCorrelationId);
    }

    AssistantAnswer composeWeatherUnavailable(
        final SourceUnavailability failure, final String traceCorrelationId) {
        final var message = formatSourceUnavailableMessage(WEATHER_SOURCE_LABEL, failure.message());
        return withTrace(
            message,
            List.of(AnswerSource.WeatherObservation.unavailable(failure)),
            traceCorrelationId);
    }

    AssistantAnswer composeCountryThenWeather(
        final CountryInfo countryInfo, final WeatherReport weatherReport, final String traceCorrelationId) {
        final var timestampLabel = timestampLabel(weatherReport.timestamp());
        final var answerText =
            COMBINED_WEATHER_TEMPLATE.formatted(
                weatherReport.location().city(),
                countryInfo.countryName(),
                formatTemperature(weatherReport.temperature()),
                timestampLabel,
                TIMESTAMP_FORMAT.format(weatherReport.timestamp().value()));
        return withTrace(
            answerText,
            List.of(
                AnswerSource.CountriesFacts.used(countryInfo),
                AnswerSource.WeatherObservation.used(weatherReport)),
            traceCorrelationId);
    }

    AssistantAnswer composeCountryThenWeatherPartial(
        final CountryInfo countryInfo,
        final SourceUnavailability weatherFailure,
        final String traceCorrelationId) {
        final var answerText =
            WEATHER_UNAVAILABLE_AFTER_COUNTRY_TEMPLATE.formatted(
                countryInfo.countryName(),
                countryInfo.capital(),
                countryInfo.capital(),
                weatherFailure.message());
        return withTrace(
            answerText,
            List.of(
                AnswerSource.CountriesFacts.used(countryInfo),
                AnswerSource.WeatherObservation.unavailable(weatherFailure)),
            traceCorrelationId);
    }

    AssistantAnswer composePlaceSynthesis(
        final CountryInfo countryInfo, final LlmResult.Success synthesis, final String traceCorrelationId) {
        final var countryFact =
            CAPITAL_FACT_TEMPLATE.formatted(countryInfo.capital(), countryInfo.countryName());
        final var answerText = countryFact + " " + synthesis.text();
        return withTrace(
            answerText,
            List.of(
                AnswerSource.CountriesFacts.used(countryInfo),
                AnswerSource.ModelSynthesis.used()),
            traceCorrelationId);
    }

    AssistantAnswer composePlaceSynthesisLlmUnavailable(
        final CountryInfo countryInfo, final SourceUnavailability synthesisFailure, final String traceCorrelationId) {
        final var countryFact =
            CAPITAL_FACT_TEMPLATE.formatted(countryInfo.capital(), countryInfo.countryName());
        final var message =
            formatSourceUnavailableMessage(OLLAMA_SOURCE_LABEL, synthesisFailure.message());
        final var answerText = countryFact + " " + message;
        return withTrace(
            answerText,
            List.of(
                AnswerSource.CountriesFacts.used(countryInfo),
                AnswerSource.ModelSynthesis.unavailable(synthesisFailure)),
            traceCorrelationId);
    }

    AssistantAnswer composeCdqProduct(
        final List<KnowledgeSnippet> snippets, final LlmResult.Success synthesis, final String traceCorrelationId) {
        return withTrace(
            synthesis.text(),
            List.of(
                AnswerSource.RagKnowledge.used(snippets),
                AnswerSource.ModelSynthesis.used()),
            traceCorrelationId);
    }

    AssistantAnswer composeCdqInsufficientKnowledge(final String traceCorrelationId) {
        return withTrace(
            INSUFFICIENT_PRODUCT_KNOWLEDGE_MESSAGE,
            List.of(AnswerSource.RagKnowledge.insufficient()),
            traceCorrelationId);
    }

    AssistantAnswer composeCdqRagUnavailable(
        final SourceUnavailability failure, final String traceCorrelationId) {
        final var message = formatSourceUnavailableMessage(RAG_SOURCE_LABEL, failure.message());
        return withTrace(
            message,
            List.of(AnswerSource.RagKnowledge.unavailable(failure)),
            traceCorrelationId);
    }

    AssistantAnswer composeCdqLlmUnavailable(
        final List<KnowledgeSnippet> snippets,
        final SourceUnavailability synthesisFailure,
        final String traceCorrelationId) {
        final var message = formatSourceUnavailableMessage(OLLAMA_SOURCE_LABEL, synthesisFailure.message());
        return withTrace(
            message,
            List.of(
                AnswerSource.RagKnowledge.used(snippets),
                AnswerSource.ModelSynthesis.unavailable(synthesisFailure)),
            traceCorrelationId);
    }

    AssistantAnswer composeUnsupported(final String reason, final String traceCorrelationId) {
        return withTrace(
            UNSUPPORTED_QUESTION_TEMPLATE.formatted(reason),
            List.of(),
            traceCorrelationId);
    }

    private static String formatWeatherAnswer(final WeatherReport weatherReport) {
        final var timestampLabel = timestampLabel(weatherReport.timestamp());
        return WEATHER_ANSWER_TEMPLATE.formatted(
            weatherReport.location().city(),
            formatTemperature(weatherReport.temperature()),
            timestampLabel,
            TIMESTAMP_FORMAT.format(weatherReport.timestamp().value()));
    }

    private static String timestampLabel(final WeatherTimestamp timestamp) {
        return switch (timestamp) {
            case final WeatherTimestamp.Observed ignored -> TIMESTAMP_LABEL_OBSERVED;
            case final WeatherTimestamp.Retrieved ignored -> TIMESTAMP_LABEL_RETRIEVED;
        };
    }

    private static String formatTemperature(final Temperature temperature) {
        return String.format(Locale.ROOT, "%.1f°C", temperature.celsius());
    }

    private static String formatSourceUnavailableMessage(final String sourceLabel, final String detail) {
        return sourceLabel + " is unavailable: " + detail;
    }

    private static AssistantAnswer withTrace(
        final String answerText, final List<AnswerSource> sources, final String traceCorrelationId) {
        return AssistantAnswer.withTrace(answerText, List.copyOf(sources), traceCorrelationId);
    }
}
