package dev.localassistant.assistant.orchestration;

import dev.localassistant.assistant.llm.LlmResult;
import dev.localassistant.assistant.question.AnswerSource;
import dev.localassistant.assistant.question.AssistantAnswer;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.Temperature;
import dev.localassistant.assistant.tools.ToolExecutionResult;
import dev.localassistant.assistant.tools.WeatherReport;
import dev.localassistant.assistant.tools.WeatherTimestamp;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class ResponseComposer {

    static final String COUNTRIES_SOURCE_LABEL = "Countries MCP";
    static final String WEATHER_SOURCE_LABEL = "Weather MCP";
    static final String RAG_SOURCE_LABEL = "RAG knowledge";
    static final String OLLAMA_SOURCE_LABEL = "Ollama chat";

    private static final String CAPITAL_ANSWER_TEMPLATE = "The capital of %s is %s.";
    private static final String CAPITAL_FACT_TEMPLATE = "%s is the capital of %s.";
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

    public AssistantAnswer composeCountryCapital(CountryInfo countryInfo, String traceCorrelationId) {
        String answerText =
                CAPITAL_ANSWER_TEMPLATE.formatted(countryInfo.countryName(), countryInfo.capital());
        return withTrace(
                answerText,
                List.of(AnswerSource.CountriesFacts.used(countryInfo)),
                traceCorrelationId);
    }

    public AssistantAnswer composeCountriesUnavailable(
            ToolExecutionResult.SourceUnavailable<CountryInfo> failure, String traceCorrelationId) {
        String message = formatSourceUnavailableMessage(COUNTRIES_SOURCE_LABEL, failure.message());
        return withTrace(
                message,
                List.of(AnswerSource.CountriesFacts.unavailable(failure.message(), failure.hint())),
                traceCorrelationId);
    }

    public AssistantAnswer composeWeatherOnly(WeatherReport weatherReport, String traceCorrelationId) {
        String answerText = formatWeatherAnswer(weatherReport);
        return withTrace(
                answerText,
                List.of(AnswerSource.WeatherObservation.used(weatherReport)),
                traceCorrelationId);
    }

    public AssistantAnswer composeWeatherUnavailable(
            ToolExecutionResult.SourceUnavailable<WeatherReport> failure, String traceCorrelationId) {
        String message = formatSourceUnavailableMessage(WEATHER_SOURCE_LABEL, failure.message());
        return withTrace(
                message,
                List.of(AnswerSource.WeatherObservation.unavailable(failure.message(), failure.hint())),
                traceCorrelationId);
    }

    public AssistantAnswer composeCountryThenWeather(
            CountryInfo countryInfo, WeatherReport weatherReport, String traceCorrelationId) {
        String timestampLabel = timestampLabel(weatherReport.timestamp());
        String answerText =
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

    public AssistantAnswer composeCountryThenWeatherPartial(
            CountryInfo countryInfo,
            ToolExecutionResult.SourceUnavailable<WeatherReport> weatherFailure,
            String traceCorrelationId) {
        String answerText =
                WEATHER_UNAVAILABLE_AFTER_COUNTRY_TEMPLATE.formatted(
                        countryInfo.countryName(),
                        countryInfo.capital(),
                        countryInfo.capital(),
                        weatherFailure.message());
        return withTrace(
                answerText,
                List.of(
                        AnswerSource.CountriesFacts.used(countryInfo),
                        AnswerSource.WeatherObservation.unavailable(
                                weatherFailure.message(), weatherFailure.hint())),
                traceCorrelationId);
    }

    public AssistantAnswer composePlaceSynthesis(
            CountryInfo countryInfo, LlmResult.Success synthesis, String traceCorrelationId) {
        String countryFact =
                CAPITAL_FACT_TEMPLATE.formatted(countryInfo.capital(), countryInfo.countryName());
        String answerText = countryFact + " " + synthesis.text();
        return withTrace(
                answerText,
                List.of(
                        AnswerSource.CountriesFacts.used(countryInfo),
                        AnswerSource.ModelSynthesis.used()),
                traceCorrelationId);
    }

    public AssistantAnswer composePlaceSynthesisCountriesUnavailable(
            ToolExecutionResult.SourceUnavailable<CountryInfo> failure, String traceCorrelationId) {
        String message = formatSourceUnavailableMessage(COUNTRIES_SOURCE_LABEL, failure.message());
        return withTrace(
                message,
                List.of(AnswerSource.CountriesFacts.unavailable(failure.message(), failure.hint())),
                traceCorrelationId);
    }

    public AssistantAnswer composePlaceSynthesisLlmUnavailable(
            CountryInfo countryInfo, LlmResult.SourceUnavailable synthesisFailure, String traceCorrelationId) {
        String countryFact =
                CAPITAL_FACT_TEMPLATE.formatted(countryInfo.capital(), countryInfo.countryName());
        String message =
                formatSourceUnavailableMessage(OLLAMA_SOURCE_LABEL, synthesisFailure.message());
        String answerText = countryFact + " " + message;
        return withTrace(
                answerText,
                List.of(
                        AnswerSource.CountriesFacts.used(countryInfo),
                        AnswerSource.ModelSynthesis.unavailable(
                                synthesisFailure.message(), synthesisFailure.hint())),
                traceCorrelationId);
    }

    public AssistantAnswer composeCdqProduct(
            List<KnowledgeSnippet> snippets, LlmResult.Success synthesis, String traceCorrelationId) {
        return withTrace(
                synthesis.text(),
                List.of(
                        AnswerSource.RagKnowledge.used(snippets),
                        AnswerSource.ModelSynthesis.used()),
                traceCorrelationId);
    }

    public AssistantAnswer composeCdqInsufficientKnowledge(String traceCorrelationId) {
        return withTrace(
                INSUFFICIENT_PRODUCT_KNOWLEDGE_MESSAGE,
                List.of(AnswerSource.RagKnowledge.insufficient()),
                traceCorrelationId);
    }

    public AssistantAnswer composeCdqRagUnavailable(
            dev.localassistant.assistant.rag.RagRetrievalResult.SourceUnavailable failure,
            String traceCorrelationId) {
        String message = formatSourceUnavailableMessage(RAG_SOURCE_LABEL, failure.message());
        return withTrace(
                message,
                List.of(AnswerSource.RagKnowledge.unavailable(failure.message(), failure.hint())),
                traceCorrelationId);
    }

    public AssistantAnswer composeCdqLlmUnavailable(
            List<KnowledgeSnippet> snippets,
            LlmResult.SourceUnavailable synthesisFailure,
            String traceCorrelationId) {
        String message = formatSourceUnavailableMessage(OLLAMA_SOURCE_LABEL, synthesisFailure.message());
        return withTrace(
                message,
                List.of(
                        AnswerSource.RagKnowledge.used(snippets),
                        AnswerSource.ModelSynthesis.unavailable(
                                synthesisFailure.message(), synthesisFailure.hint())),
                traceCorrelationId);
    }

    public AssistantAnswer composeUnsupported(String reason, String traceCorrelationId) {
        return withTrace(
                UNSUPPORTED_QUESTION_TEMPLATE.formatted(reason),
                List.of(),
                traceCorrelationId);
    }

    private static String formatWeatherAnswer(WeatherReport weatherReport) {
        String timestampLabel = timestampLabel(weatherReport.timestamp());
        return WEATHER_ANSWER_TEMPLATE.formatted(
                weatherReport.location().city(),
                formatTemperature(weatherReport.temperature()),
                timestampLabel,
                TIMESTAMP_FORMAT.format(weatherReport.timestamp().value()));
    }

    private static String timestampLabel(WeatherTimestamp timestamp) {
        return switch (timestamp) {
            case WeatherTimestamp.Observed ignored -> TIMESTAMP_LABEL_OBSERVED;
            case WeatherTimestamp.Retrieved ignored -> TIMESTAMP_LABEL_RETRIEVED;
        };
    }

    private static String formatTemperature(Temperature temperature) {
        return String.format(Locale.ROOT, "%.1f°C", temperature.celsius());
    }

    private static String formatSourceUnavailableMessage(String sourceLabel, String detail) {
        return sourceLabel + " is unavailable: " + detail;
    }

    private static AssistantAnswer withTrace(
            String answerText, List<AnswerSource> sources, String traceCorrelationId) {
        return AssistantAnswer.withTrace(answerText, List.copyOf(sources), traceCorrelationId);
    }
}
