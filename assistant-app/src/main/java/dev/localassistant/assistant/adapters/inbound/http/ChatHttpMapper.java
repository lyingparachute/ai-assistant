package dev.localassistant.assistant.adapters.inbound.http;

import dev.localassistant.assistant.question.AnswerSource;
import dev.localassistant.assistant.question.AssistantAnswer;
import dev.localassistant.assistant.question.ConversationTurn;
import dev.localassistant.assistant.question.UserQuestion;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.WeatherReport;
import dev.localassistant.assistant.tools.WeatherTimestamp;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
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
        return switch (source) {
            case AnswerSource.CountriesFacts.Used used ->
                    new SourceResponse.CountriesFacts(
                            used.status().name(),
                            toCountryInfoResponse(used.resolvedCountryInfo()),
                            null,
                            null);
            case AnswerSource.CountriesFacts.Unavailable unavailable ->
                    new SourceResponse.CountriesFacts(
                            unavailable.status().name(),
                            null,
                            unavailable.unavailability().message(),
                            unavailable.unavailability().hint());
        };
    }

    private SourceResponse.WeatherObservation toWeatherObservation(
            AnswerSource.WeatherObservation source) {
        return switch (source) {
            case AnswerSource.WeatherObservation.Used used ->
                    new SourceResponse.WeatherObservation(
                            used.status().name(),
                            toWeatherReportResponse(used.resolvedWeatherReport()),
                            null,
                            null);
            case AnswerSource.WeatherObservation.Unavailable unavailable ->
                    new SourceResponse.WeatherObservation(
                            unavailable.status().name(),
                            null,
                            unavailable.unavailability().message(),
                            unavailable.unavailability().hint());
        };
    }

    private SourceResponse.RagKnowledge toRagKnowledge(AnswerSource.RagKnowledge source) {
        return switch (source) {
            case AnswerSource.RagKnowledge.Used used ->
                    new SourceResponse.RagKnowledge(
                            used.status().name(),
                            used.snippets().stream().map(this::toSnippetResponse).toList(),
                            null,
                            null);
            case AnswerSource.RagKnowledge.Insufficient insufficient ->
                    new SourceResponse.RagKnowledge(insufficient.status().name(), null, null, null);
            case AnswerSource.RagKnowledge.Unavailable unavailable ->
                    new SourceResponse.RagKnowledge(
                            unavailable.status().name(),
                            null,
                            unavailable.unavailability().message(),
                            unavailable.unavailability().hint());
        };
    }

    private SourceResponse.ModelSynthesis toModelSynthesis(AnswerSource.ModelSynthesis source) {
        return switch (source) {
            case AnswerSource.ModelSynthesis.Used used ->
                    new SourceResponse.ModelSynthesis(used.status().name(), null, null);
            case AnswerSource.ModelSynthesis.Unavailable unavailable ->
                    new SourceResponse.ModelSynthesis(
                            unavailable.status().name(),
                            unavailable.unavailability().message(),
                            unavailable.unavailability().hint());
        };
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
        return new KnowledgeSnippetResponse(
                snippet.chunkText(),
                snippet.sourceUrl(),
                snippet.contentHash(),
                snippet.chunkIndex(),
                snippet.retrievalScore().value());
    }
}
