package dev.localassistant.assistant.orchestration;

import dev.localassistant.assistant.llm.LlmPort;
import dev.localassistant.assistant.llm.LlmResult;
import dev.localassistant.assistant.llm.PromptContext;
import dev.localassistant.assistant.question.AssistantAnswer;
import dev.localassistant.assistant.question.ConversationTurn;
import dev.localassistant.assistant.question.UserQuestion;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import dev.localassistant.assistant.rag.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.RagRetrievalResult;
import dev.localassistant.assistant.tools.CountriesPort;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.ToolExecutionResult;
import dev.localassistant.assistant.tools.WeatherPort;
import dev.localassistant.assistant.tools.WeatherReport;

import java.util.ArrayList;
import java.util.List;

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

    public ConversationTurn answer(UserQuestion question) {
        AssistantRequestTrace trace = AssistantRequestTrace.start(question);
        RoutedQuestion routed = sourceRoutingPolicy.route(question);
        trace.routeSelected(routed.route());

        AssistantAnswer answer =
                switch (routed.route()) {
                    case COUNTRY_CAPITAL -> handleCountryCapital(routed, trace);
                    case WEATHER_LOCATION -> handleWeatherLocation(routed, trace);
                    case COUNTRY_THEN_WEATHER -> handleCountryThenWeather(routed, trace);
                    case PLACE_SYNTHESIS -> handlePlaceSynthesis(routed, trace);
                    case CDQ_PRODUCT -> handleCdqProduct(routed, trace);
                    case UNSUPPORTED -> handleUnsupported(routed, trace);
                };

        trace.completed(outcomeLabel(routed.route(), answer));
        return new ConversationTurn(question, answer);
    }

    private AssistantAnswer handleCountryCapital(RoutedQuestion routed, AssistantRequestTrace trace) {
        ToolExecutionResult<CountryInfo> result = lookupCountry(routed, trace);
        return switch (result) {
            case ToolExecutionResult.Success<CountryInfo> success ->
                    responseComposer.composeCountryCapital(success.value(), trace.correlationId());
            case ToolExecutionResult.SourceUnavailable<CountryInfo> unavailable ->
                    responseComposer.composeCountriesUnavailable(unavailable, trace.correlationId());
            case ToolExecutionResult.ToolError<CountryInfo> toolError ->
                    responseComposer.composeCountriesUnavailable(
                            new ToolExecutionResult.SourceUnavailable<>(
                                    ResponseComposer.COUNTRIES_SOURCE_LABEL,
                                    toolError.error(),
                                    toolError.hint()),
                            trace.correlationId());
        };
    }

    private AssistantAnswer handleWeatherLocation(RoutedQuestion routed, AssistantRequestTrace trace) {
        String location = routed.weatherLocation().orElseThrow().city();
        trace.portInvoked(WEATHER_PORT_NAME);
        ToolExecutionResult<WeatherReport> result = weatherPort.currentWeather(location);
        return switch (result) {
            case ToolExecutionResult.Success<WeatherReport> success ->
                    responseComposer.composeWeatherOnly(success.value(), trace.correlationId());
            case ToolExecutionResult.SourceUnavailable<WeatherReport> unavailable ->
                    responseComposer.composeWeatherUnavailable(unavailable, trace.correlationId());
            case ToolExecutionResult.ToolError<WeatherReport> toolError ->
                    responseComposer.composeWeatherUnavailable(
                            new ToolExecutionResult.SourceUnavailable<>(
                                    ResponseComposer.WEATHER_SOURCE_LABEL,
                                    toolError.error(),
                                    toolError.hint()),
                            trace.correlationId());
        };
    }

    private AssistantAnswer handleCountryThenWeather(RoutedQuestion routed, AssistantRequestTrace trace) {
        ToolExecutionResult<CountryInfo> countryResult = lookupCountry(routed, trace);
        if (countryResult instanceof ToolExecutionResult.SourceUnavailable<CountryInfo> unavailable) {
            return responseComposer.composeCountriesUnavailable(unavailable, trace.correlationId());
        }
        if (countryResult instanceof ToolExecutionResult.ToolError<CountryInfo> toolError) {
            return responseComposer.composeCountriesUnavailable(
                    new ToolExecutionResult.SourceUnavailable<>(
                            ResponseComposer.COUNTRIES_SOURCE_LABEL,
                            toolError.error(),
                            toolError.hint()),
                    trace.correlationId());
        }

        CountryInfo countryInfo = ((ToolExecutionResult.Success<CountryInfo>) countryResult).value();
        trace.portInvoked(WEATHER_PORT_NAME);
        ToolExecutionResult<WeatherReport> weatherResult =
                weatherPort.currentWeather(countryInfo.capital());

        return switch (weatherResult) {
            case ToolExecutionResult.Success<WeatherReport> success ->
                    responseComposer.composeCountryThenWeather(
                            countryInfo, success.value(), trace.correlationId());
            case ToolExecutionResult.SourceUnavailable<WeatherReport> unavailable ->
                    responseComposer.composeCountryThenWeatherPartial(
                            countryInfo, unavailable, trace.correlationId());
            case ToolExecutionResult.ToolError<WeatherReport> toolError ->
                    responseComposer.composeCountryThenWeatherPartial(
                            countryInfo,
                            new ToolExecutionResult.SourceUnavailable<>(
                                    ResponseComposer.WEATHER_SOURCE_LABEL,
                                    toolError.error(),
                                    toolError.hint()),
                            trace.correlationId());
        };
    }

    private AssistantAnswer handlePlaceSynthesis(RoutedQuestion routed, AssistantRequestTrace trace) {
        String placeName = routed.placeName().orElseThrow();
        trace.portInvoked(COUNTRIES_PORT_NAME);
        ToolExecutionResult<CountryInfo> countryResult = countriesPort.lookupCountry(placeName);

        if (countryResult instanceof ToolExecutionResult.SourceUnavailable<CountryInfo> unavailable) {
            return responseComposer.composePlaceSynthesisCountriesUnavailable(
                    unavailable, trace.correlationId());
        }
        if (countryResult instanceof ToolExecutionResult.ToolError<CountryInfo> toolError) {
            return responseComposer.composePlaceSynthesisCountriesUnavailable(
                    new ToolExecutionResult.SourceUnavailable<>(
                            ResponseComposer.COUNTRIES_SOURCE_LABEL,
                            toolError.error(),
                            toolError.hint()),
                    trace.correlationId());
        }

        CountryInfo countryInfo = ((ToolExecutionResult.Success<CountryInfo>) countryResult).value();
        List<String> groundedFacts = countryFactsForSynthesis(countryInfo);
        trace.portInvoked(LLM_PORT_NAME);
        LlmResult synthesis =
                llmPort.generate(
                        new PromptContext(
                                routed.question().text(), groundedFacts, PLACE_SYNTHESIS_INSTRUCTIONS));

        return switch (synthesis) {
            case LlmResult.Success success ->
                    responseComposer.composePlaceSynthesis(
                            countryInfo, success, trace.correlationId());
            case LlmResult.SourceUnavailable unavailable ->
                    responseComposer.composePlaceSynthesisLlmUnavailable(
                            countryInfo, unavailable, trace.correlationId());
        };
    }

    private AssistantAnswer handleCdqProduct(RoutedQuestion routed, AssistantRequestTrace trace) {
        trace.portInvoked(RAG_PORT_NAME);
        RagRetrievalResult retrieval =
                ragKnowledgePort.retrieve(routed.question().text(), ragRetrievalPolicy);

        return switch (retrieval) {
            case RagRetrievalResult.NoRelevantKnowledge ignored -> {
                trace.ragRetrieval(0);
                yield responseComposer.composeCdqInsufficientKnowledge(trace.correlationId());
            }
            case RagRetrievalResult.SourceUnavailable unavailable -> {
                trace.ragRetrieval(0);
                yield responseComposer.composeCdqRagUnavailable(unavailable, trace.correlationId());
            }
            case RagRetrievalResult.Success success -> {
                trace.ragRetrieval(success.snippets().size());
                List<String> groundedFacts = compactSnippetFacts(success.snippets());
                trace.portInvoked(LLM_PORT_NAME);
                LlmResult synthesis =
                        llmPort.generate(
                                new PromptContext(
                                        routed.question().text(),
                                        groundedFacts,
                                        CDQ_SYNTHESIS_INSTRUCTIONS));
                yield switch (synthesis) {
                    case LlmResult.Success llmSuccess ->
                            responseComposer.composeCdqProduct(
                                    success.snippets(), llmSuccess, trace.correlationId());
                    case LlmResult.SourceUnavailable llmUnavailable ->
                            responseComposer.composeCdqLlmUnavailable(
                                    success.snippets(), llmUnavailable, trace.correlationId());
                };
            }
        };
    }

    private AssistantAnswer handleUnsupported(RoutedQuestion routed, AssistantRequestTrace trace) {
        String reason = routed.unsupportedReason().orElse("unsupported question");
        return responseComposer.composeUnsupported(reason, trace.correlationId());
    }

    private ToolExecutionResult<CountryInfo> lookupCountry(
            RoutedQuestion routed, AssistantRequestTrace trace) {
        String lookupKey = routed.countryLookupKey().orElseThrow();
        trace.portInvoked(COUNTRIES_PORT_NAME);
        return countriesPort.lookupCountry(lookupKey);
    }

    private static List<String> countryFactsForSynthesis(CountryInfo countryInfo) {
        List<String> facts = new ArrayList<>();
        facts.add(
                CAPITAL_FACT_TEMPLATE.formatted(countryInfo.capital(), countryInfo.countryName()));
        facts.add("Country: " + countryInfo.countryName());
        facts.add("Capital: " + countryInfo.capital());
        facts.add("Region: " + countryInfo.region());
        facts.add("Population: " + countryInfo.population());
        return List.copyOf(facts);
    }

    private static List<String> compactSnippetFacts(List<KnowledgeSnippet> snippets) {
        return snippets.stream().map(KnowledgeSnippet::chunkText).toList();
    }

    private static String outcomeLabel(QuestionRoute route, AssistantAnswer answer) {
        return route.name().toLowerCase() + "_answered_sources=" + answer.sources().size();
    }

    private static final String CAPITAL_FACT_TEMPLATE = "%s is the capital of %s.";
}
