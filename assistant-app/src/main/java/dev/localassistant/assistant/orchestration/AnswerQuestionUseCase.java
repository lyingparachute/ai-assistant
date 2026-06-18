package dev.localassistant.assistant.orchestration;

import dev.localassistant.assistant.llm.LlmPort;
import dev.localassistant.assistant.llm.LlmResult;
import dev.localassistant.assistant.llm.PromptContext;
import dev.localassistant.assistant.question.AssistantAnswer;
import dev.localassistant.assistant.question.ConversationTurn;
import dev.localassistant.assistant.question.SourceContributionStatus;
import dev.localassistant.assistant.question.UserQuestion;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import dev.localassistant.assistant.rag.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.RagRetrievalResult;
import dev.localassistant.assistant.tools.CountriesPort;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.SourceUnavailability;
import dev.localassistant.assistant.tools.ToolExecutionResult;
import dev.localassistant.assistant.tools.WeatherPort;
import dev.localassistant.assistant.tools.WeatherReport;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public final class AnswerQuestionUseCase {

    private static final String COUNTRIES_PORT_NAME = "CountriesPort";
    private static final String WEATHER_PORT_NAME = "WeatherPort";
    private static final String RAG_PORT_NAME = "RagKnowledgePort";
    private static final String LLM_PORT_NAME = "LlmPort";

    private static final String PLACE_SYNTHESIS_INSTRUCTIONS =
            "Answer concisely using only the grounded country facts. "
                    + "Do not present any specific factual claim that is not in the grounded facts as verified.";
    private static final String CDQ_SYNTHESIS_INSTRUCTIONS =
            "Answer using only the grounded product knowledge snippets. "
                    + "Do not invent product features not supported by the snippets.";

    private final CountriesPort countriesPort;
    private final WeatherPort weatherPort;
    private final RagKnowledgePort ragKnowledgePort;
    private final LlmPort llmPort;
    private final SourceRoutingPolicy sourceRoutingPolicy;
    private final ResponseComposer responseComposer;
    private final RagRetrievalPolicy ragRetrievalPolicy;

    public AnswerQuestionUseCase(
            CountriesPort countriesPort,
            WeatherPort weatherPort,
            RagKnowledgePort ragKnowledgePort,
            LlmPort llmPort,
            SourceRoutingPolicy sourceRoutingPolicy,
            ResponseComposer responseComposer,
            RagRetrievalPolicy ragRetrievalPolicy) {
        this.countriesPort = countriesPort;
        this.weatherPort = weatherPort;
        this.ragKnowledgePort = ragKnowledgePort;
        this.llmPort = llmPort;
        this.sourceRoutingPolicy = sourceRoutingPolicy;
        this.responseComposer = responseComposer;
        this.ragRetrievalPolicy = ragRetrievalPolicy;
    }

    public ConversationTurn answer(UserQuestion question, AssistantResponseSink sink) {
        AssistantRequestTrace trace = AssistantRequestTrace.start(question);
        RoutedQuestion routed = sourceRoutingPolicy.route(question);
        trace.routeSelected(routed.route());

        AssistantAnswer answer =
                switch (routed) {
                    case RoutedQuestion.CountryCapital countryCapital ->
                            handleCountryCapital(countryCapital, trace, sink);
                    case RoutedQuestion.WeatherOnly weatherOnly ->
                            handleWeatherLocation(weatherOnly, trace, sink);
                    case RoutedQuestion.CountryThenWeather countryThenWeather ->
                            handleCountryThenWeather(countryThenWeather, trace, sink);
                    case RoutedQuestion.PlaceSynthesis placeSynthesis ->
                            handlePlaceSynthesis(placeSynthesis, trace, sink);
                    case RoutedQuestion.CdqProduct cdqProduct ->
                            handleCdqProduct(cdqProduct, trace, sink);
                    case RoutedQuestion.Unsupported unsupported ->
                            handleUnsupported(unsupported, trace);
                };

        trace.completed(answer.sources().size());
        ConversationTurn turn = new ConversationTurn(question, answer);
        sink.complete(turn);
        return turn;
    }

    private AssistantAnswer handleCountryCapital(
            RoutedQuestion.CountryCapital routed, AssistantRequestTrace trace, AssistantResponseSink sink) {
        ToolExecutionResult<CountryInfo> result = lookupCountry(routed.countryLookupKey(), trace, sink);
        return switch (result) {
            case ToolExecutionResult.Success<CountryInfo> success ->
                    responseComposer.composeCountryCapital(success.value(), trace.correlationId());
            case ToolExecutionResult.SourceUnavailable<CountryInfo> failure ->
                    composeCountriesUnavailable(failure, trace);
            case ToolExecutionResult.ToolError<CountryInfo> failure ->
                    composeCountriesUnavailable(failure, trace);
        };
    }

    private AssistantAnswer handleWeatherLocation(
            RoutedQuestion.WeatherOnly routed, AssistantRequestTrace trace, AssistantResponseSink sink) {
        String location = routed.weatherLocation().city();
        trace.portInvoked(WEATHER_PORT_NAME);
        ToolExecutionResult<WeatherReport> result = weatherPort.currentWeather(location);
        recordWeatherOutcome(sink, result);
        return switch (result) {
            case ToolExecutionResult.Success<WeatherReport> success ->
                    responseComposer.composeWeatherOnly(success.value(), trace.correlationId());
            case ToolExecutionResult.SourceUnavailable<WeatherReport> failure ->
                    composeWeatherUnavailable(failure, trace);
            case ToolExecutionResult.ToolError<WeatherReport> failure ->
                    composeWeatherUnavailable(failure, trace);
        };
    }

    private AssistantAnswer handleCountryThenWeather(
            RoutedQuestion.CountryThenWeather routed, AssistantRequestTrace trace, AssistantResponseSink sink) {
        ToolExecutionResult<CountryInfo> countryResult =
                lookupCountry(routed.countryLookupKey(), trace, sink);
        return switch (countryResult) {
            case ToolExecutionResult.SourceUnavailable<CountryInfo> failure ->
                    composeCountriesUnavailable(failure, trace);
            case ToolExecutionResult.ToolError<CountryInfo> failure ->
                    composeCountriesUnavailable(failure, trace);
            case ToolExecutionResult.Success<CountryInfo> success ->
                    continueWithWeather(success.value(), trace, sink);
        };
    }

    private AssistantAnswer continueWithWeather(
            CountryInfo countryInfo, AssistantRequestTrace trace, AssistantResponseSink sink) {
        trace.portInvoked(WEATHER_PORT_NAME);
        ToolExecutionResult<WeatherReport> weatherResult =
                weatherPort.currentWeather(countryInfo.capital());
        recordWeatherOutcome(sink, weatherResult);
        return switch (weatherResult) {
            case ToolExecutionResult.Success<WeatherReport> success ->
                    responseComposer.composeCountryThenWeather(
                            countryInfo, success.value(), trace.correlationId());
            case ToolExecutionResult.SourceUnavailable<WeatherReport> failure ->
                    responseComposer.composeCountryThenWeatherPartial(
                            countryInfo, weatherUnavailable(failure), trace.correlationId());
            case ToolExecutionResult.ToolError<WeatherReport> failure ->
                    responseComposer.composeCountryThenWeatherPartial(
                            countryInfo, weatherUnavailable(failure), trace.correlationId());
        };
    }

    private SourceUnavailability countriesUnavailable(ToolExecutionResult<CountryInfo> failure) {
        return failure.asUnavailability(ResponseComposer.COUNTRIES_SOURCE_LABEL);
    }

    private SourceUnavailability weatherUnavailable(ToolExecutionResult<WeatherReport> failure) {
        return failure.asUnavailability(ResponseComposer.WEATHER_SOURCE_LABEL);
    }

    private AssistantAnswer handlePlaceSynthesis(
            RoutedQuestion.PlaceSynthesis routed, AssistantRequestTrace trace, AssistantResponseSink sink) {
        ToolExecutionResult<CountryInfo> countryResult = lookupCountry(routed.placeName(), trace, sink);
        return switch (countryResult) {
            case ToolExecutionResult.SourceUnavailable<CountryInfo> failure ->
                    responseComposer.composeCountriesUnavailable(
                            countriesUnavailable(failure), trace.correlationId());
            case ToolExecutionResult.ToolError<CountryInfo> failure ->
                    responseComposer.composeCountriesUnavailable(
                            countriesUnavailable(failure), trace.correlationId());
            case ToolExecutionResult.Success<CountryInfo> success ->
                    synthesizePlace(routed, success.value(), trace, sink);
        };
    }

    private AssistantAnswer synthesizePlace(
            RoutedQuestion.PlaceSynthesis routed,
            CountryInfo countryInfo,
            AssistantRequestTrace trace,
            AssistantResponseSink sink) {
        PromptContext prompt =
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
            PromptContext prompt,
            AssistantRequestTrace trace,
            AssistantResponseSink sink,
            BiFunction<LlmResult.Success, String, AssistantAnswer> onSuccess,
            BiFunction<SourceUnavailability, String, AssistantAnswer> onUnavailable) {
        trace.portInvoked(LLM_PORT_NAME);
        LlmResult synthesis = llmPort.generate(prompt, sink::appendAnswerToken);
        sink.recordSourceOutcome(SourceType.MODEL_SYNTHESIS, statusFor(synthesis));
        return switch (synthesis) {
            case LlmResult.Success success -> onSuccess.apply(success, trace.correlationId());
            case LlmResult.SourceUnavailable unavailable ->
                    onUnavailable.apply(unavailable.asUnavailability(), trace.correlationId());
        };
    }

    private AssistantAnswer handleCdqProduct(
            RoutedQuestion.CdqProduct routed, AssistantRequestTrace trace, AssistantResponseSink sink) {
        trace.portInvoked(RAG_PORT_NAME);
        RagRetrievalResult retrieval =
                ragKnowledgePort.retrieve(routed.question().text(), ragRetrievalPolicy);
        sink.recordSourceOutcome(SourceType.RAG_KNOWLEDGE, statusFor(retrieval));

        return switch (retrieval) {
            case RagRetrievalResult.NoRelevantKnowledge ignored -> {
                trace.ragRetrieval(0);
                yield responseComposer.composeCdqInsufficientKnowledge(trace.correlationId());
            }
            case RagRetrievalResult.SourceUnavailable unavailable -> {
                trace.ragRetrieval(0);
                yield responseComposer.composeCdqRagUnavailable(
                        unavailable.asUnavailability(), trace.correlationId());
            }
            case RagRetrievalResult.Success success -> {
                trace.ragRetrieval(success.snippets().size());
                PromptContext prompt =
                        new PromptContext(
                                routed.question().text(),
                                compactSnippetFacts(success.snippets()),
                                CDQ_SYNTHESIS_INSTRUCTIONS);
                yield synthesize(
                        prompt,
                        trace,
                        sink,
                        (llmSuccess, traceId) ->
                                responseComposer.composeCdqProduct(
                                        success.snippets(), llmSuccess, traceId),
                        (failure, traceId) ->
                                responseComposer.composeCdqLlmUnavailable(
                                        success.snippets(), failure, traceId));
            }
        };
    }

    private AssistantAnswer handleUnsupported(
            RoutedQuestion.Unsupported routed, AssistantRequestTrace trace) {
        return responseComposer.composeUnsupported(routed.reason(), trace.correlationId());
    }

    private ToolExecutionResult<CountryInfo> lookupCountry(
            String lookupKey, AssistantRequestTrace trace, AssistantResponseSink sink) {
        trace.portInvoked(COUNTRIES_PORT_NAME);
        ToolExecutionResult<CountryInfo> result = countriesPort.lookupCountry(lookupKey);
        sink.recordSourceOutcome(SourceType.COUNTRIES_FACTS, statusFor(result));
        return result;
    }

    private AssistantAnswer composeCountriesUnavailable(
            ToolExecutionResult<CountryInfo> failure, AssistantRequestTrace trace) {
        return responseComposer.composeCountriesUnavailable(
                countriesUnavailable(failure), trace.correlationId());
    }

    private AssistantAnswer composeWeatherUnavailable(
            ToolExecutionResult<WeatherReport> failure, AssistantRequestTrace trace) {
        return responseComposer.composeWeatherUnavailable(
                weatherUnavailable(failure), trace.correlationId());
    }

    private static void recordWeatherOutcome(
            AssistantResponseSink sink, ToolExecutionResult<WeatherReport> result) {
        sink.recordSourceOutcome(SourceType.WEATHER_OBSERVATION, statusFor(result));
    }

    private static SourceContributionStatus statusFor(ToolExecutionResult<?> result) {
        return switch (result) {
            case ToolExecutionResult.Success<?> ignored -> SourceContributionStatus.USED;
            case ToolExecutionResult.SourceUnavailable<?> ignored ->
                    SourceContributionStatus.UNAVAILABLE;
            case ToolExecutionResult.ToolError<?> ignored -> SourceContributionStatus.UNAVAILABLE;
        };
    }

    private static SourceContributionStatus statusFor(RagRetrievalResult result) {
        return switch (result) {
            case RagRetrievalResult.Success ignored -> SourceContributionStatus.USED;
            case RagRetrievalResult.NoRelevantKnowledge ignored -> SourceContributionStatus.INSUFFICIENT;
            case RagRetrievalResult.SourceUnavailable ignored -> SourceContributionStatus.UNAVAILABLE;
        };
    }

    private static SourceContributionStatus statusFor(LlmResult result) {
        return switch (result) {
            case LlmResult.Success ignored -> SourceContributionStatus.USED;
            case LlmResult.SourceUnavailable ignored -> SourceContributionStatus.UNAVAILABLE;
        };
    }

    private static List<String> countryFactsForSynthesis(CountryInfo countryInfo) {
        List<String> facts = new ArrayList<>();
        facts.add(
                ResponseComposer.CAPITAL_FACT_TEMPLATE.formatted(
                        countryInfo.capital(), countryInfo.countryName()));
        facts.add("Country: " + countryInfo.countryName());
        facts.add("Capital: " + countryInfo.capital());
        facts.add("Region: " + countryInfo.region());
        facts.add("Population: " + countryInfo.population());
        return List.copyOf(facts);
    }

    private static List<String> compactSnippetFacts(List<KnowledgeSnippet> snippets) {
        return snippets.stream().map(KnowledgeSnippet::chunkText).toList();
    }
}
