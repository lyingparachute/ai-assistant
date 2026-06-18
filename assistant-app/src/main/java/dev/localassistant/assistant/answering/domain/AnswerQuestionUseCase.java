package dev.localassistant.assistant.answering.domain;

import dev.localassistant.assistant.answering.domain.port.inbound.AnswerQuestion;
import dev.localassistant.assistant.countryfacts.domain.CountryInfo;
import dev.localassistant.assistant.countryfacts.domain.port.inbound.ResolveCountryFacts;
import dev.localassistant.assistant.rag.domain.KnowledgeSnippet;
import dev.localassistant.assistant.rag.domain.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.domain.RagRetrievalResult;
import dev.localassistant.assistant.rag.domain.port.inbound.RetrieveRagKnowledge;
import dev.localassistant.assistant.shared.SourceUnavailability;
import dev.localassistant.assistant.shared.ToolExecutionResult;
import dev.localassistant.assistant.synthesis.domain.LlmResult;
import dev.localassistant.assistant.synthesis.domain.PromptContext;
import dev.localassistant.assistant.synthesis.domain.port.outbound.LlmPort;
import dev.localassistant.assistant.weather.domain.Location;
import dev.localassistant.assistant.weather.domain.WeatherReport;
import dev.localassistant.assistant.weather.domain.port.inbound.ResolveWeatherObservation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public final class AnswerQuestionUseCase implements AnswerQuestion {

    private static final String COUNTRIES_PORT_NAME = "CountriesPort";
    private static final String WEATHER_PORT_NAME = "WeatherPort";
    private static final String RAG_PORT_NAME = "RetrieveRagKnowledge";
    private static final String LLM_PORT_NAME = "LlmPort";

    private static final String PLACE_SYNTHESIS_INSTRUCTIONS =
        "Answer concisely using only the grounded country facts. "
            + "Do not present any specific factual claim that is not in the grounded facts as verified.";
    private static final String CDQ_SYNTHESIS_INSTRUCTIONS =
        "Answer using only the grounded product knowledge snippets. "
            + "Do not invent product features not supported by the snippets.";

    private final ResolveCountryFacts resolveCountryFacts;
    private final ResolveWeatherObservation resolveWeatherObservation;
    private final RetrieveRagKnowledge retrieveRagKnowledge;
    private final LlmPort llmPort;
    private final SourceRoutingPolicy sourceRoutingPolicy;
    private final ResponseComposer responseComposer;
    private final RagRetrievalPolicy ragRetrievalPolicy;

    public AnswerQuestionUseCase(
        final ResolveCountryFacts resolveCountryFacts,
        final ResolveWeatherObservation resolveWeatherObservation,
        final RetrieveRagKnowledge retrieveRagKnowledge,
        final LlmPort llmPort,
        final RagRetrievalPolicy ragRetrievalPolicy) {
        this(
            resolveCountryFacts,
            resolveWeatherObservation,
            retrieveRagKnowledge,
            llmPort,
            new SourceRoutingPolicy(),
            new ResponseComposer(),
            ragRetrievalPolicy);
    }

    AnswerQuestionUseCase(
        final ResolveCountryFacts resolveCountryFacts,
        final ResolveWeatherObservation resolveWeatherObservation,
        final RetrieveRagKnowledge retrieveRagKnowledge,
        final LlmPort llmPort,
        final SourceRoutingPolicy sourceRoutingPolicy,
        final ResponseComposer responseComposer,
        final RagRetrievalPolicy ragRetrievalPolicy) {
        this.resolveCountryFacts = resolveCountryFacts;
        this.resolveWeatherObservation = resolveWeatherObservation;
        this.retrieveRagKnowledge = retrieveRagKnowledge;
        this.llmPort = llmPort;
        this.sourceRoutingPolicy = sourceRoutingPolicy;
        this.responseComposer = responseComposer;
        this.ragRetrievalPolicy = ragRetrievalPolicy;
    }

    @Override
    public ConversationTurn execute(final Command command) {
        final var question = command.question();
        final var sink = command.sink();
        final var trace = AssistantRequestTrace.start(question);
        final var routed = sourceRoutingPolicy.route(question);
        trace.routeSelected(routed.route());

        final var answer =
            switch (routed) {
                case final RoutedQuestion.CountryCapital countryCapital -> handleCountryCapital(countryCapital, trace, sink);
                case final RoutedQuestion.WeatherOnly weatherOnly -> handleWeatherLocation(weatherOnly, trace, sink);
                case final RoutedQuestion.CountryThenWeather countryThenWeather -> handleCountryThenWeather(countryThenWeather, trace, sink);
                case final RoutedQuestion.PlaceSynthesis placeSynthesis -> handlePlaceSynthesis(placeSynthesis, trace, sink);
                case final RoutedQuestion.CdqProduct cdqProduct -> handleCdqProduct(cdqProduct, trace, sink);
                case final RoutedQuestion.Unsupported unsupported -> handleUnsupported(unsupported, trace);
            };

        trace.completed(answer.sources().size());
        final var turn = new ConversationTurn(question, answer);
        sink.complete(turn);
        return turn;
    }

    private AssistantAnswer handleCountryCapital(
        final RoutedQuestion.CountryCapital routed, final AssistantRequestTrace trace, final AssistantResponseSink sink) {
        final var result = lookupCountry(routed.countryLookupKey(), trace, sink);
        return switch (result) {
            case ToolExecutionResult.Success<CountryInfo>(final var countryInfo) -> responseComposer.composeCountryCapital(countryInfo, trace.correlationId());
            case final ToolExecutionResult.SourceUnavailable<CountryInfo> failure -> composeCountriesUnavailable(failure, trace);
            case final ToolExecutionResult.ToolError<CountryInfo> failure -> composeCountriesUnavailable(failure, trace);
        };
    }

    private AssistantAnswer handleWeatherLocation(
        final RoutedQuestion.WeatherOnly routed, final AssistantRequestTrace trace, final AssistantResponseSink sink) {
        trace.portInvoked(WEATHER_PORT_NAME);
        final var result = resolveWeatherObservation.execute(
            new ResolveWeatherObservation.Command(routed.weatherLocation()));
        recordWeatherOutcome(sink, result);
        return switch (result) {
            case ToolExecutionResult.Success<WeatherReport>(final var weatherReport) -> responseComposer.composeWeatherOnly(weatherReport, trace.correlationId());
            case final ToolExecutionResult.SourceUnavailable<WeatherReport> failure -> composeWeatherUnavailable(failure, trace);
            case final ToolExecutionResult.ToolError<WeatherReport> failure -> composeWeatherUnavailable(failure, trace);
        };
    }

    private AssistantAnswer handleCountryThenWeather(
        final RoutedQuestion.CountryThenWeather routed, final AssistantRequestTrace trace, final AssistantResponseSink sink) {
        final var countryResult = lookupCountry(routed.countryLookupKey(), trace, sink);
        return switch (countryResult) {
            case final ToolExecutionResult.SourceUnavailable<CountryInfo> failure -> composeCountriesUnavailable(failure, trace);
            case final ToolExecutionResult.ToolError<CountryInfo> failure -> composeCountriesUnavailable(failure, trace);
            case ToolExecutionResult.Success<CountryInfo>(final var countryInfo) -> continueWithWeather(countryInfo, trace, sink);
        };
    }

    private AssistantAnswer continueWithWeather(
        final CountryInfo countryInfo, final AssistantRequestTrace trace, final AssistantResponseSink sink) {
        trace.portInvoked(WEATHER_PORT_NAME);
        final var weatherResult = resolveWeatherObservation.execute(
            new ResolveWeatherObservation.Command(Location.of(countryInfo.capital())));
        recordWeatherOutcome(sink, weatherResult);
        return switch (weatherResult) {
            case ToolExecutionResult.Success<WeatherReport>(final var weatherReport) -> responseComposer.composeCountryThenWeather(
                countryInfo, weatherReport, trace.correlationId());
            case final ToolExecutionResult.SourceUnavailable<WeatherReport> failure -> responseComposer.composeCountryThenWeatherPartial(
                countryInfo, weatherUnavailable(failure), trace.correlationId());
            case final ToolExecutionResult.ToolError<WeatherReport> failure -> responseComposer.composeCountryThenWeatherPartial(
                countryInfo, weatherUnavailable(failure), trace.correlationId());
        };
    }

    private SourceUnavailability countriesUnavailable(final ToolExecutionResult<CountryInfo> failure) {
        return failure.asUnavailability(ResponseComposer.COUNTRIES_SOURCE_LABEL);
    }

    private SourceUnavailability weatherUnavailable(final ToolExecutionResult<WeatherReport> failure) {
        return failure.asUnavailability(ResponseComposer.WEATHER_SOURCE_LABEL);
    }

    private AssistantAnswer handlePlaceSynthesis(
        final RoutedQuestion.PlaceSynthesis routed, final AssistantRequestTrace trace, final AssistantResponseSink sink) {
        final var countryResult = lookupCountry(routed.placeName(), trace, sink);
        return switch (countryResult) {
            case final ToolExecutionResult.SourceUnavailable<CountryInfo> failure -> responseComposer.composeCountriesUnavailable(
                countriesUnavailable(failure), trace.correlationId());
            case final ToolExecutionResult.ToolError<CountryInfo> failure -> responseComposer.composeCountriesUnavailable(
                countriesUnavailable(failure), trace.correlationId());
            case ToolExecutionResult.Success<CountryInfo>(final var countryInfo) -> synthesizePlace(routed, countryInfo, trace, sink);
        };
    }

    private AssistantAnswer synthesizePlace(
        final RoutedQuestion.PlaceSynthesis routed,
        final CountryInfo countryInfo,
        final AssistantRequestTrace trace,
        final AssistantResponseSink sink) {
        final var prompt =
            new PromptContext(
                routed.question().text(),
                countryFactsForSynthesis(countryInfo),
                PLACE_SYNTHESIS_INSTRUCTIONS);
        return synthesize(
            prompt,
            trace,
            sink,
            (success, traceId) ->
                responseComposer.composePlaceSynthesis(countryInfo, success, traceId),
            (failure, traceId) ->
                responseComposer.composePlaceSynthesisLlmUnavailable(
                    countryInfo, failure, traceId));
    }

    private AssistantAnswer synthesize(
        final PromptContext prompt,
        final AssistantRequestTrace trace,
        final AssistantResponseSink sink,
        final BiFunction<LlmResult.Success, String, AssistantAnswer> onSuccess,
        final BiFunction<SourceUnavailability, String, AssistantAnswer> onUnavailable) {
        trace.portInvoked(LLM_PORT_NAME);
        final var synthesis = llmPort.generate(prompt, sink::appendAnswerToken);
        sink.recordSourceOutcome(SourceType.MODEL_SYNTHESIS, statusFor(synthesis));
        return switch (synthesis) {
            case final LlmResult.Success success -> onSuccess.apply(success, trace.correlationId());
            case LlmResult.SourceUnavailable(final var unavailability) -> onUnavailable.apply(unavailability, trace.correlationId());
        };
    }

    private AssistantAnswer handleCdqProduct(
        final RoutedQuestion.CdqProduct routed, final AssistantRequestTrace trace, final AssistantResponseSink sink) {
        trace.portInvoked(RAG_PORT_NAME);
        final var retrieval =
            retrieveRagKnowledge.execute(
                new RetrieveRagKnowledge.Command(routed.question().text(), ragRetrievalPolicy));
        sink.recordSourceOutcome(SourceType.RAG_KNOWLEDGE, statusFor(retrieval));

        return switch (retrieval) {
            case final RagRetrievalResult.NoRelevantKnowledge ignored -> {
                trace.ragRetrieval(0);
                yield responseComposer.composeCdqInsufficientKnowledge(trace.correlationId());
            }
            case RagRetrievalResult.SourceUnavailable(final var unavailability) -> {
                trace.ragRetrieval(0);
                yield responseComposer.composeCdqRagUnavailable(
                    unavailability, trace.correlationId());
            }
            case RagRetrievalResult.Success(final var snippets) -> {
                trace.ragRetrieval(snippets.size());
                final var prompt =
                    new PromptContext(
                        routed.question().text(),
                        compactSnippetFacts(snippets),
                        CDQ_SYNTHESIS_INSTRUCTIONS);
                yield synthesize(
                    prompt,
                    trace,
                    sink,
                    (llmSuccess, traceId) ->
                        responseComposer.composeCdqProduct(snippets, llmSuccess, traceId),
                    (failure, traceId) ->
                        responseComposer.composeCdqLlmUnavailable(snippets, failure, traceId));
            }
        };
    }

    private AssistantAnswer handleUnsupported(
        final RoutedQuestion.Unsupported routed, final AssistantRequestTrace trace) {
        return responseComposer.composeUnsupported(routed.reason(), trace.correlationId());
    }

    private ToolExecutionResult<CountryInfo> lookupCountry(
        final String lookupKey, final AssistantRequestTrace trace, final AssistantResponseSink sink) {
        trace.portInvoked(COUNTRIES_PORT_NAME);
        final var result = resolveCountryFacts.execute(new ResolveCountryFacts.Command(lookupKey));
        sink.recordSourceOutcome(SourceType.COUNTRIES_FACTS, statusFor(result));
        return result;
    }

    private AssistantAnswer composeCountriesUnavailable(
        final ToolExecutionResult<CountryInfo> failure, final AssistantRequestTrace trace) {
        return responseComposer.composeCountriesUnavailable(
            countriesUnavailable(failure), trace.correlationId());
    }

    private AssistantAnswer composeWeatherUnavailable(
        final ToolExecutionResult<WeatherReport> failure, final AssistantRequestTrace trace) {
        return responseComposer.composeWeatherUnavailable(
            weatherUnavailable(failure), trace.correlationId());
    }

    private static void recordWeatherOutcome(
        final AssistantResponseSink sink, final ToolExecutionResult<WeatherReport> result) {
        sink.recordSourceOutcome(SourceType.WEATHER_OBSERVATION, statusFor(result));
    }

    private static SourceContributionStatus statusFor(final ToolExecutionResult<?> result) {
        return switch (result) {
            case final ToolExecutionResult.Success<?> ignored -> SourceContributionStatus.USED;
            case final ToolExecutionResult.SourceUnavailable<?> ignored -> SourceContributionStatus.UNAVAILABLE;
            case final ToolExecutionResult.ToolError<?> ignored -> SourceContributionStatus.UNAVAILABLE;
        };
    }

    private static SourceContributionStatus statusFor(final RagRetrievalResult result) {
        return switch (result) {
            case final RagRetrievalResult.Success ignored -> SourceContributionStatus.USED;
            case final RagRetrievalResult.NoRelevantKnowledge ignored -> SourceContributionStatus.INSUFFICIENT;
            case final RagRetrievalResult.SourceUnavailable ignored -> SourceContributionStatus.UNAVAILABLE;
        };
    }

    private static SourceContributionStatus statusFor(final LlmResult result) {
        return switch (result) {
            case final LlmResult.Success ignored -> SourceContributionStatus.USED;
            case final LlmResult.SourceUnavailable ignored -> SourceContributionStatus.UNAVAILABLE;
        };
    }

    private static List<String> countryFactsForSynthesis(final CountryInfo countryInfo) {
        final var facts = new ArrayList<String>();
        facts.add(
            ResponseComposer.CAPITAL_FACT_TEMPLATE.formatted(
                countryInfo.capital(), countryInfo.countryName()));
        facts.add("Country: " + countryInfo.countryName());
        facts.add("Capital: " + countryInfo.capital());
        facts.add("Region: " + countryInfo.region());
        facts.add("Population: " + countryInfo.population());
        return List.copyOf(facts);
    }

    private static List<String> compactSnippetFacts(final List<KnowledgeSnippet> snippets) {
        return snippets.stream().map(KnowledgeSnippet::chunkText).toList();
    }
}
