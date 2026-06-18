package dev.localassistant.assistant.orchestration;

import dev.localassistant.assistant.llm.LlmResult;
import dev.localassistant.assistant.llm.support.StubLlmPort;
import dev.localassistant.assistant.orchestration.support.RecordingAssistantResponseSink;
import dev.localassistant.assistant.orchestration.support.StubCountriesPort;
import dev.localassistant.assistant.orchestration.support.StubRagKnowledgePort;
import dev.localassistant.assistant.orchestration.support.StubWeatherPort;
import dev.localassistant.assistant.question.ConversationTurn;
import dev.localassistant.assistant.question.SourceContributionStatus;
import dev.localassistant.assistant.question.UserQuestion;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.rag.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.RagRetrievalResult;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.Location;
import dev.localassistant.assistant.tools.Temperature;
import dev.localassistant.assistant.tools.ToolExecutionResult;
import dev.localassistant.assistant.tools.WeatherReport;
import dev.localassistant.assistant.tools.WeatherTimestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerQuestionUseCaseSinkRouteMatrixTest {

    private static final CountryInfo GERMANY =
            new CountryInfo("Germany", "Berlin", "Europe", 83_240_525L);
    private static final WeatherReport BERLIN_WEATHER =
            new WeatherReport(
                    Location.of("Berlin"),
                    Temperature.celsius(15.0),
                    new WeatherTimestamp.Observed(Instant.parse("2026-06-16T11:30:00Z")));
    private static final WeatherReport MUNICH_WEATHER =
            new WeatherReport(
                    Location.of("Munich"),
                    Temperature.celsius(18.3),
                    new WeatherTimestamp.Retrieved(Instant.parse("2026-06-16T12:00:00Z")));
    private static final KnowledgeSnippet CDQ_SNIPPET =
            KnowledgeSnippet.fromRetrieval(
                    "CDQ Fraud Guard monitors supplier data quality.",
                    "https://www.cdq.com/products/cdq-fraud-guard",
                    "hash",
                    0,
                    0.82);

    private StubCountriesPort countriesPort;
    private StubWeatherPort weatherPort;
    private StubRagKnowledgePort ragKnowledgePort;
    private StubLlmPort llmPort;
    private AnswerQuestionUseCase useCase;

    @BeforeEach
    void setUp() {
        countriesPort = new StubCountriesPort();
        weatherPort = new StubWeatherPort();
        ragKnowledgePort = new StubRagKnowledgePort();
        llmPort =
                new StubLlmPort(
                        (context, tokenSink) -> {
                            tokenSink.accept("streamed ");
                            tokenSink.accept("answer");
                            return new LlmResult.Success("streamed answer");
                        });
        useCase =
                new AnswerQuestionUseCase(
                        countriesPort,
                        weatherPort,
                        ragKnowledgePort,
                        llmPort,
                        new SourceRoutingPolicy(),
                        new ResponseComposer(),
                        new RagRetrievalPolicy(5, 0.5));
        countriesPort.register("Germany", new ToolExecutionResult.Success<>(GERMANY));
        countriesPort.register("Berlin", new ToolExecutionResult.Success<>(GERMANY));
        weatherPort.register("Berlin", new ToolExecutionResult.Success<>(BERLIN_WEATHER));
        weatherPort.register("Munich", new ToolExecutionResult.Success<>(MUNICH_WEATHER));
        ragKnowledgePort.onRetrieve(
                (question, policy) -> new RagRetrievalResult.Success(List.of(CDQ_SNIPPET)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allSixRoutes")
    void traceMultisetMatchesFinalSources(String routeLabel, String question) {
        RecordingAssistantResponseSink sink = new RecordingAssistantResponseSink();
        ConversationTurn turn = useCase.answer(UserQuestion.of(question), sink);

        assertTraceFinalParity(sink, turn);
        assertThat(sink.completedTurn()).isEqualTo(turn);
        assertThat(sink.orderedEvents()).endsWith("complete");
    }

    static Stream<Arguments> allSixRoutes() {
        return Stream.of(
                Arguments.of("CountryCapital", "What is the capital city of Germany?"),
                Arguments.of("WeatherOnly", "What is the temperature currently in Munich?"),
                Arguments.of(
                        "CountryThenWeather",
                        "What is the temperature of the capital of Germany currently?"),
                Arguments.of("PlaceSynthesis", "What do you know about Berlin?"),
                Arguments.of("CdqProduct", "What is CDQ Fraud Guard?"),
                Arguments.of("Unsupported", "Who won the World Cup in 2022?"));
    }


    @Test
    void unsupportedRouteEmitsNoTraceEvents() {
        RecordingAssistantResponseSink sink = new RecordingAssistantResponseSink();

        ConversationTurn turn =
                useCase.answer(UserQuestion.of("Who won the World Cup in 2022?"), sink);

        assertThat(sink.traceEvents()).isEmpty();
        assertThat(turn.answer().sources()).isEmpty();
        assertThat(sink.answerTokens()).isEmpty();
    }

    @Test
    void modelSynthesisTraceRecordedAfterTokens() {
        RecordingAssistantResponseSink sink = new RecordingAssistantResponseSink();

        useCase.answer(UserQuestion.of("What do you know about Berlin?"), sink);

        assertThat(sink.answerTokens()).containsExactly("streamed ", "answer");
        int lastTokenIndex = lastIndexOf(sink.orderedEvents(), "token");
        int modelTraceIndex =
                sink.orderedEvents()
                        .indexOf(
                                "trace:"
                                        + SourceType.MODEL_SYNTHESIS
                                        + ":"
                                        + SourceContributionStatus.USED);
        assertThat(lastTokenIndex).isGreaterThanOrEqualTo(0);
        assertThat(modelTraceIndex).isGreaterThan(lastTokenIndex);
    }

    @Test
    void tokenThenSourceUnavailableFinalOmitsStreamedText() {
        llmPort =
                new StubLlmPort(
                        (context, tokenSink) -> {
                            tokenSink.accept("fabricated ");
                            tokenSink.accept("guess");
                            return new LlmResult.SourceUnavailable(
                                    "Ollama chat", "model offline", "start Ollama");
                        });
        useCase =
                new AnswerQuestionUseCase(
                        countriesPort,
                        weatherPort,
                        ragKnowledgePort,
                        llmPort,
                        new SourceRoutingPolicy(),
                        new ResponseComposer(),
                        new RagRetrievalPolicy(5, 0.5));

        RecordingAssistantResponseSink sink = new RecordingAssistantResponseSink();
        ConversationTurn turn =
                useCase.answer(UserQuestion.of("What do you know about Berlin?"), sink);

        assertThat(sink.answerTokens()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(turn.answer().answerText()).doesNotContain("fabricated").doesNotContain("guess");
        assertThat(turn.answer().answerText()).contains("Ollama chat is unavailable");
        assertTraceFinalParity(sink, turn);
        int lastTokenIndex = lastIndexOf(sink.orderedEvents(), "token");
        int modelTraceIndex =
                sink.orderedEvents()
                        .indexOf(
                                "trace:"
                                        + SourceType.MODEL_SYNTHESIS
                                        + ":"
                                        + SourceContributionStatus.UNAVAILABLE);
        assertThat(modelTraceIndex).isGreaterThan(lastTokenIndex);
    }

    private static void assertTraceFinalParity(
            RecordingAssistantResponseSink sink, ConversationTurn turn) {
        List<RecordingAssistantResponseSink.TraceEvent> expected =
                turn.answer().sources().stream()
                        .map(
                                source ->
                                        new RecordingAssistantResponseSink.TraceEvent(
                                                SourceType.from(source), source.status()))
                        .toList();
        assertThat(sink.traceEvents()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static int lastIndexOf(List<String> events, String event) {
        for (int index = events.size() - 1; index >= 0; index--) {
            if (event.equals(events.get(index))) {
                return index;
            }
        }
        return -1;
    }
}
