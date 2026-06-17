package dev.localassistant.assistant.adapters.inbound.http;

import dev.localassistant.assistant.question.AnswerSource;
import dev.localassistant.assistant.question.AssistantAnswer;
import dev.localassistant.assistant.question.ConversationTurn;
import dev.localassistant.assistant.question.SourceContributionStatus;
import dev.localassistant.assistant.question.UserQuestion;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.WeatherReport;
import dev.localassistant.assistant.tools.WeatherTimestamp;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ChatHttpMapper {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ISO_INSTANT.withLocale(Locale.ROOT);

    UserQuestion toUserQuestion(ChatRequest request) {
        return UserQuestion.of(request.question());
    }

    ChatResponse toChatResponse(ConversationTurn turn) {
        AssistantAnswer answer = turn.answer();
        String traceCorrelationId =
                answer.traceCorrelationId().orElse(null);
        List<SourceResponse> sources = answer.sources().stream().map(this::toSourceResponse).toList();
        return new ChatResponse(answer.answerText(), sources, traceCorrelationId);
    }

    private SourceResponse toSourceResponse(AnswerSource source) {
        return switch (source) {
            case AnswerSource.CountriesFacts countriesFacts -> toCountriesFacts(countriesFacts);
            case AnswerSource.WeatherObservation weatherObservation ->
                    toWeatherObservation(weatherObservation);
            case AnswerSource.RagKnowledge ragKnowledge -> toRagKnowledge(ragKnowledge);
            case AnswerSource.ModelSynthesis modelSynthesis -> toModelSynthesis(modelSynthesis);
        };
    }

    private SourceResponse.CountriesFacts toCountriesFacts(AnswerSource.CountriesFacts source) {
        CountryInfoResponse countryInfo =
                source.countryInfo().map(this::toCountryInfoResponse).orElse(null);
        String unavailableMessage = blankToNull(source.unavailableMessage());
        String unavailableHint = blankToNull(source.unavailableHint());
        return new SourceResponse.CountriesFacts(
                source.status().name(), countryInfo, unavailableMessage, unavailableHint);
    }

    private SourceResponse.WeatherObservation toWeatherObservation(
            AnswerSource.WeatherObservation source) {
        WeatherReportResponse weatherReport =
                source.weatherReport().map(this::toWeatherReportResponse).orElse(null);
        String unavailableMessage = blankToNull(source.unavailableMessage());
        String unavailableHint = blankToNull(source.unavailableHint());
        return new SourceResponse.WeatherObservation(
                source.status().name(), weatherReport, unavailableMessage, unavailableHint);
    }

    private SourceResponse.RagKnowledge toRagKnowledge(AnswerSource.RagKnowledge source) {
        List<KnowledgeSnippetResponse> snippets =
                source.status() == SourceContributionStatus.USED
                        ? source.snippets().stream().map(this::toSnippetResponse).toList()
                        : null;
        String unavailableMessage = blankToNull(source.unavailableMessage());
        String unavailableHint = blankToNull(source.unavailableHint());
        return new SourceResponse.RagKnowledge(
                source.status().name(), snippets, unavailableMessage, unavailableHint);
    }

    private SourceResponse.ModelSynthesis toModelSynthesis(AnswerSource.ModelSynthesis source) {
        String unavailableMessage = blankToNull(source.unavailableMessage());
        String unavailableHint = blankToNull(source.unavailableHint());
        return new SourceResponse.ModelSynthesis(
                source.status().name(), unavailableMessage, unavailableHint);
    }

    private CountryInfoResponse toCountryInfoResponse(CountryInfo countryInfo) {
        return new CountryInfoResponse(
                countryInfo.countryName(),
                countryInfo.capital(),
                countryInfo.region(),
                countryInfo.population());
    }

    private WeatherReportResponse toWeatherReportResponse(WeatherReport report) {
        WeatherTimestamp timestamp = report.timestamp();
        String kind =
                switch (timestamp) {
                    case WeatherTimestamp.Observed ignored -> "observed";
                    case WeatherTimestamp.Retrieved ignored -> "retrieved";
                };
        return new WeatherReportResponse(
                new WeatherReportResponse.LocationResponse(report.location().city()),
                new WeatherReportResponse.TemperatureResponse(report.temperature().celsius()),
                new WeatherReportResponse.TimestampResponse(
                        kind, TIMESTAMP_FORMAT.format(timestamp.value())));
    }

    private KnowledgeSnippetResponse toSnippetResponse(KnowledgeSnippet snippet) {
        Double similarityScore =
                snippet.similarityScore().map(Double::doubleValue).orElse(null);
        return new KnowledgeSnippetResponse(
                snippet.chunkText(),
                snippet.sourceUrl(),
                snippet.contentHash(),
                snippet.chunkIndex(),
                similarityScore);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
