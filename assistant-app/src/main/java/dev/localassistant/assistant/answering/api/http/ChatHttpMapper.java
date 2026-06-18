package dev.localassistant.assistant.answering.api.http;

import dev.localassistant.assistant.answering.domain.AnswerSource;
import dev.localassistant.assistant.answering.domain.ConversationTurn;
import dev.localassistant.assistant.answering.domain.SourceContributionStatus;
import dev.localassistant.assistant.answering.domain.UserQuestion;
import dev.localassistant.assistant.countryfacts.domain.CountryInfo;
import dev.localassistant.assistant.rag.domain.KnowledgeSnippet;
import dev.localassistant.assistant.weather.domain.WeatherReport;
import dev.localassistant.assistant.weather.domain.WeatherTimestamp;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@Profile("!ingest-rag")
final class ChatHttpMapper {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ISO_INSTANT.withLocale(Locale.ROOT);

    UserQuestion toUserQuestion(final ChatRequest request) {
        return UserQuestion.of(request.question());
    }

    ChatResponse toChatResponse(final ConversationTurn turn) {
        final var answer = turn.answer();
        final var traceCorrelationId = answer.traceCorrelationId().orElse(null);
        final var sources = answer.sources().stream().map(this::toSourceResponse).toList();
        return new ChatResponse(answer.answerText(), sources, traceCorrelationId);
    }

    private SourceResponse toSourceResponse(final AnswerSource source) {
        return switch (source) {
            case final AnswerSource.CountriesFacts countriesFacts -> toCountriesFacts(countriesFacts);
            case final AnswerSource.WeatherObservation weatherObservation -> toWeatherObservation(weatherObservation);
            case final AnswerSource.RagKnowledge ragKnowledge -> toRagKnowledge(ragKnowledge);
            case final AnswerSource.ModelSynthesis modelSynthesis -> toModelSynthesis(modelSynthesis);
        };
    }

    private SourceResponse.CountriesFacts toCountriesFacts(final AnswerSource.CountriesFacts source) {
        return switch (source) {
            case AnswerSource.CountriesFacts.Used(final var resolvedCountryInfo) -> new SourceResponse.CountriesFacts(
                SourceContributionStatus.USED.name(),
                toCountryInfoResponse(resolvedCountryInfo),
                null,
                null);
            case AnswerSource.CountriesFacts.Unavailable(final var unavailability) -> new SourceResponse.CountriesFacts(
                SourceContributionStatus.UNAVAILABLE.name(),
                null,
                unavailability.message(),
                unavailability.hint());
        };
    }

    private SourceResponse.WeatherObservation toWeatherObservation(
        final AnswerSource.WeatherObservation source) {
        return switch (source) {
            case AnswerSource.WeatherObservation.Used(final var resolvedWeatherReport) -> new SourceResponse.WeatherObservation(
                SourceContributionStatus.USED.name(),
                toWeatherReportResponse(resolvedWeatherReport),
                null,
                null);
            case AnswerSource.WeatherObservation.Unavailable(final var unavailability) -> new SourceResponse.WeatherObservation(
                SourceContributionStatus.UNAVAILABLE.name(),
                null,
                unavailability.message(),
                unavailability.hint());
        };
    }

    private SourceResponse.RagKnowledge toRagKnowledge(final AnswerSource.RagKnowledge source) {
        return switch (source) {
            case AnswerSource.RagKnowledge.Used(final var resolvedSnippets) -> new SourceResponse.RagKnowledge(
                SourceContributionStatus.USED.name(),
                resolvedSnippets.stream().map(this::toSnippetResponse).toList(),
                null,
                null);
            case final AnswerSource.RagKnowledge.Insufficient ignored -> new SourceResponse.RagKnowledge(
                SourceContributionStatus.INSUFFICIENT.name(), null, null, null);
            case AnswerSource.RagKnowledge.Unavailable(final var unavailability) -> new SourceResponse.RagKnowledge(
                SourceContributionStatus.UNAVAILABLE.name(),
                null,
                unavailability.message(),
                unavailability.hint());
        };
    }

    private SourceResponse.ModelSynthesis toModelSynthesis(final AnswerSource.ModelSynthesis source) {
        return switch (source) {
            case final AnswerSource.ModelSynthesis.Used ignored -> new SourceResponse.ModelSynthesis(
                SourceContributionStatus.USED.name(), null, null);
            case AnswerSource.ModelSynthesis.Unavailable(final var unavailability) -> new SourceResponse.ModelSynthesis(
                SourceContributionStatus.UNAVAILABLE.name(),
                unavailability.message(),
                unavailability.hint());
        };
    }

    private CountryInfoResponse toCountryInfoResponse(final CountryInfo countryInfo) {
        return new CountryInfoResponse(
            countryInfo.countryName(),
            countryInfo.capital(),
            countryInfo.region(),
            countryInfo.population());
    }

    private WeatherReportResponse toWeatherReportResponse(final WeatherReport report) {
        final var timestamp = report.timestamp();
        final var kind =
            switch (timestamp) {
                case final WeatherTimestamp.Observed ignored -> "observed";
                case final WeatherTimestamp.Retrieved ignored -> "retrieved";
            };
        return new WeatherReportResponse(
            new WeatherReportResponse.LocationResponse(report.location().city()),
            new WeatherReportResponse.TemperatureResponse(report.temperature().celsius()),
            new WeatherReportResponse.TimestampResponse(
                kind, TIMESTAMP_FORMAT.format(timestamp.value())));
    }

    private KnowledgeSnippetResponse toSnippetResponse(final KnowledgeSnippet snippet) {
        return new KnowledgeSnippetResponse(
            snippet.chunkText(),
            snippet.sourceUrl(),
            snippet.contentHash(),
            snippet.chunkIndex(),
            snippet.retrievalScore().value());
    }
}
