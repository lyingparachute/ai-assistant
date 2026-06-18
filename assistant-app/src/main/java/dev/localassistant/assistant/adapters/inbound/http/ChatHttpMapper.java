package dev.localassistant.assistant.adapters.inbound.http;

import dev.localassistant.assistant.question.AnswerSource;
import dev.localassistant.assistant.question.ConversationTurn;
import dev.localassistant.assistant.question.SourceContributionStatus;
import dev.localassistant.assistant.question.UserQuestion;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.WeatherReport;
import dev.localassistant.assistant.tools.WeatherTimestamp;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
@Profile("!ingest-rag")
final class ChatHttpMapper {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ISO_INSTANT.withLocale(Locale.ROOT);

    UserQuestion toUserQuestion(ChatRequest request) {
        return UserQuestion.of(request.question());
    }

    ChatResponse toChatResponse(ConversationTurn turn) {
        final var answer = turn.answer();
        final var traceCorrelationId = answer.traceCorrelationId().orElse(null);
        final var sources = answer.sources().stream().map(this::toSourceResponse).toList();
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
            case AnswerSource.CountriesFacts.Used(var resolvedCountryInfo) ->
                    new SourceResponse.CountriesFacts(
                            SourceContributionStatus.USED.name(),
                            toCountryInfoResponse(resolvedCountryInfo),
                            null,
                            null);
            case AnswerSource.CountriesFacts.Unavailable(var unavailability) ->
                    new SourceResponse.CountriesFacts(
                            SourceContributionStatus.UNAVAILABLE.name(),
                            null,
                            unavailability.message(),
                            unavailability.hint());
        };
    }

    private SourceResponse.WeatherObservation toWeatherObservation(
            AnswerSource.WeatherObservation source) {
        return switch (source) {
            case AnswerSource.WeatherObservation.Used(var resolvedWeatherReport) ->
                    new SourceResponse.WeatherObservation(
                            SourceContributionStatus.USED.name(),
                            toWeatherReportResponse(resolvedWeatherReport),
                            null,
                            null);
            case AnswerSource.WeatherObservation.Unavailable(var unavailability) ->
                    new SourceResponse.WeatherObservation(
                            SourceContributionStatus.UNAVAILABLE.name(),
                            null,
                            unavailability.message(),
                            unavailability.hint());
        };
    }

    private SourceResponse.RagKnowledge toRagKnowledge(AnswerSource.RagKnowledge source) {
        return switch (source) {
            case AnswerSource.RagKnowledge.Used(var resolvedSnippets) ->
                    new SourceResponse.RagKnowledge(
                            SourceContributionStatus.USED.name(),
                            resolvedSnippets.stream().map(this::toSnippetResponse).toList(),
                            null,
                            null);
            case AnswerSource.RagKnowledge.Insufficient ignored ->
                    new SourceResponse.RagKnowledge(
                            SourceContributionStatus.INSUFFICIENT.name(), null, null, null);
            case AnswerSource.RagKnowledge.Unavailable(var unavailability) ->
                    new SourceResponse.RagKnowledge(
                            SourceContributionStatus.UNAVAILABLE.name(),
                            null,
                            unavailability.message(),
                            unavailability.hint());
        };
    }

    private SourceResponse.ModelSynthesis toModelSynthesis(AnswerSource.ModelSynthesis source) {
        return switch (source) {
            case AnswerSource.ModelSynthesis.Used ignored ->
                    new SourceResponse.ModelSynthesis(
                            SourceContributionStatus.USED.name(), null, null);
            case AnswerSource.ModelSynthesis.Unavailable(var unavailability) ->
                    new SourceResponse.ModelSynthesis(
                            SourceContributionStatus.UNAVAILABLE.name(),
                            unavailability.message(),
                            unavailability.hint());
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
        final var timestamp = report.timestamp();
        final var kind =
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
