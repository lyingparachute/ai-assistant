package dev.localassistant.assistant.orchestration;

import dev.localassistant.assistant.llm.LlmResult;
import dev.localassistant.assistant.llm.PromptContext;
import dev.localassistant.assistant.llm.support.StubLlmPort;
import dev.localassistant.assistant.question.AnswerSource;
import dev.localassistant.assistant.question.AssistantAnswer;
import dev.localassistant.assistant.question.ConversationTurn;
import dev.localassistant.assistant.question.SourceContributionStatus;
import dev.localassistant.assistant.question.UserQuestion;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.rag.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.RagRetrievalResult;
import dev.localassistant.assistant.orchestration.support.RecordingAssistantResponseSink;
import dev.localassistant.assistant.orchestration.support.StubCountriesPort;
import dev.localassistant.assistant.orchestration.support.StubRagKnowledgePort;
import dev.localassistant.assistant.orchestration.support.StubWeatherPort;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.Location;
import dev.localassistant.assistant.tools.Temperature;
import dev.localassistant.assistant.tools.ToolExecutionResult;
import dev.localassistant.assistant.tools.WeatherReport;
import dev.localassistant.assistant.tools.WeatherTimestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerQuestionUseCaseTest {

    private static final CountryInfo GERMANY =
            new CountryInfo("Germany", "Berlin", "Europe", 83_240_525L);
    private static final WeatherReport MUNICH_WEATHER =
            new WeatherReport(
                    Location.of("Munich"),
                    Temperature.celsius(18.3),
                    new WeatherTimestamp.Retrieved(Instant.parse("2026-06-16T12:00:00Z")));
    private static final WeatherReport BERLIN_WEATHER =
            new WeatherReport(
                    Location.of("Berlin"),
                    Temperature.celsius(15.0),
                    new WeatherTimestamp.Observed(Instant.parse("2026-06-16T11:30:00Z")));
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
        llmPort = new StubLlmPort(new LlmResult.Success("synthesized answer"));
        useCase =
                new AnswerQuestionUseCase(
                        countriesPort,
                        weatherPort,
                        ragKnowledgePort,
                        llmPort,
                        new SourceRoutingPolicy(),
                        new ResponseComposer(),
                        new RagRetrievalPolicy(5, 0.5));
    }

    @Test
    void germanyCapitalUsesCountriesOnlyWithoutLlm() {
        countriesPort.register("Germany", new ToolExecutionResult.Success<>(GERMANY));

        ConversationTurn turn =
                answer(UserQuestion.of("What is the capital city of Germany?"));

        assertThat(countriesPort.invocationCount()).isEqualTo(1);
        assertThat(weatherPort.invocationCount()).isZero();
        assertThat(ragKnowledgePort.retrieveInvocationCount()).isZero();
        assertThat(llmPort.invocationCount()).isZero();
        assertThat(turn.answer().answerText()).contains("Berlin");
        assertThat(turn.answer().sources()).singleElement().isInstanceOf(AnswerSource.CountriesFacts.class);
        assertThat(turn.answer().traceCorrelationId()).isPresent();
    }

    @Test
    void munichWeatherUnavailableDoesNotInventTemperature() {
        weatherPort.register(
                "Munich",
                new ToolExecutionResult.SourceUnavailable<>(
                        "weather MCP", "weather service down", "retry later"));

        ConversationTurn turn =
                answer(UserQuestion.of("What is the temperature currently in Munich?"));

        assertThat(weatherPort.invocationCount()).isEqualTo(1);
        assertThat(llmPort.invocationCount()).isZero();
        assertThat(turn.answer().answerText()).contains("Weather MCP is unavailable");
        assertThat(turn.answer().answerText()).doesNotContain("°C");
        assertThat(((AnswerSource.WeatherObservation) turn.answer().sources().getFirst()).status())
                .isEqualTo(SourceContributionStatus.UNAVAILABLE);
    }

    @Test
    void germanyCapitalCountriesUnavailableDoesNotInventCapital() {
        countriesPort.register(
                "Germany",
                new ToolExecutionResult.SourceUnavailable<>(
                        "countries MCP", "countries service down", "retry later"));

        ConversationTurn turn =
                answer(UserQuestion.of("What is the capital city of Germany?"));

        assertThat(countriesPort.invocationCount()).isEqualTo(1);
        assertThat(llmPort.invocationCount()).isZero();
        assertThat(turn.answer().answerText()).contains("Countries MCP is unavailable");
        assertThat(turn.answer().answerText()).doesNotContain("Berlin");
    }

    @Test
    void cdqRagUnavailableAtUseCaseLevel() {
        ragKnowledgePort.onRetrieve(
                (question, policy) ->
                        new RagRetrievalResult.SourceUnavailable(
                                "pgvector RAG", "database down", "start postgres"));

        ConversationTurn turn = answer(UserQuestion.of("What is CDQ Fraud Guard?"));

        assertThat(ragKnowledgePort.retrieveInvocationCount()).isEqualTo(1);
        assertThat(llmPort.invocationCount()).isZero();
        assertThat(turn.answer().answerText()).contains("RAG knowledge is unavailable");
    }

    @Test
    void cdqLlmUnavailableKeepsRagFactsWithoutInventingProductText() {
        ragKnowledgePort.onRetrieve(
                (question, policy) -> new RagRetrievalResult.Success(List.of(CDQ_SNIPPET)));
        llmPort =
                new StubLlmPort(
                        new LlmResult.SourceUnavailable(
                                "Ollama chat", "model offline", "start Ollama"));
        useCase =
                new AnswerQuestionUseCase(
                        countriesPort,
                        weatherPort,
                        ragKnowledgePort,
                        llmPort,
                        new SourceRoutingPolicy(),
                        new ResponseComposer(),
                        new RagRetrievalPolicy(5, 0.5));

        ConversationTurn turn = answer(UserQuestion.of("What is CDQ Fraud Guard?"));

        assertThat(llmPort.invocationCount()).isEqualTo(1);
        assertThat(((AnswerSource.RagKnowledge) turn.answer().sources().get(0)).status())
                .isEqualTo(SourceContributionStatus.USED);
        assertThat(((AnswerSource.ModelSynthesis) turn.answer().sources().get(1)).status())
                .isEqualTo(SourceContributionStatus.UNAVAILABLE);
        assertThat(turn.answer().answerText()).contains("Ollama chat is unavailable");
        assertThat(turn.answer().answerText()).doesNotContain("monitors supplier");
    }

    @Test
    void unsupportedQuestionInvokesNoPorts() {
        ConversationTurn turn =
                answer(UserQuestion.of("Who won the World Cup in 2022?"));

        assertThat(countriesPort.invocationCount()).isZero();
        assertThat(weatherPort.invocationCount()).isZero();
        assertThat(ragKnowledgePort.retrieveInvocationCount()).isZero();
        assertThat(llmPort.invocationCount()).isZero();
        assertThat(turn.answer().answerText()).contains("cannot answer");
    }

    @Test
    void munichWeatherUsesWeatherOnly() {
        weatherPort.register("Munich", new ToolExecutionResult.Success<>(MUNICH_WEATHER));

        ConversationTurn turn =
                answer(UserQuestion.of("What is the temperature currently in Munich?"));

        assertThat(weatherPort.invocationCount()).isEqualTo(1);
        assertThat(countriesPort.invocationCount()).isZero();
        assertThat(llmPort.invocationCount()).isZero();
        assertThat(turn.answer().answerText()).contains("Munich").contains("18.3°C").contains("Retrieved:");
        assertThat(turn.answer().sources()).singleElement().isInstanceOf(AnswerSource.WeatherObservation.class);
    }

    @Test
    void germanyCapitalTemperatureChainsCountriesThenWeather() {
        countriesPort.register("Germany", new ToolExecutionResult.Success<>(GERMANY));
        weatherPort.register("Berlin", new ToolExecutionResult.Success<>(BERLIN_WEATHER));

        ConversationTurn turn =
                answer(
                        UserQuestion.of("What is the temperature of the capital of Germany currently?"));

        assertThat(countriesPort.invocationCount()).isEqualTo(1);
        assertThat(weatherPort.invocationCount()).isEqualTo(1);
        assertThat(llmPort.invocationCount()).isZero();
        assertThat(turn.answer().sources()).hasSize(2);
        assertThat(turn.answer().answerText()).contains("Berlin").contains("Germany");
    }

    @Test
    void berlinPlaceSynthesisUsesCountriesAndLlmOnly() {
        countriesPort.register("Berlin", new ToolExecutionResult.Success<>(GERMANY));
        llmPort =
                new StubLlmPort(
                        context -> {
                            assertThat(context.groundedFacts())
                                    .anyMatch(fact -> fact.contains("Berlin is the capital of Germany"));
                            return new LlmResult.Success("Berlin is an important city in Germany.");
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

        ConversationTurn turn = answer(UserQuestion.of("What do you know about Berlin?"));

        assertThat(countriesPort.invocationCount()).isEqualTo(1);
        assertThat(llmPort.invocationCount()).isEqualTo(1);
        assertThat(weatherPort.invocationCount()).isZero();
        assertThat(ragKnowledgePort.retrieveInvocationCount()).isZero();
        assertThat(turn.answer().answerText()).contains("Berlin is the capital of Germany");
        assertThat(turn.answer().sources()).hasSize(2);
        assertThat(turn.answer().sources().get(0)).isInstanceOf(AnswerSource.CountriesFacts.class);
        assertThat(turn.answer().sources().get(1)).isInstanceOf(AnswerSource.ModelSynthesis.class);
        assertThat(turn.answer().sources().get(1)).isNotInstanceOf(AnswerSource.WeatherObservation.class);
    }

    @Test
    void cdqProductQuestionRetrievesRagAndCallsLlmWithCompactSnippets() {
        ragKnowledgePort.onRetrieve(
                (question, policy) -> new RagRetrievalResult.Success(List.of(CDQ_SNIPPET)));
        llmPort =
                new StubLlmPort(
                        context -> {
                            assertThat(context.groundedFacts()).containsExactly(CDQ_SNIPPET.chunkText());
                            return new LlmResult.Success("Fraud Guard monitors supplier data quality.");
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

        ConversationTurn turn = answer(UserQuestion.of("What is CDQ Fraud Guard?"));

        assertThat(ragKnowledgePort.retrieveInvocationCount()).isEqualTo(1);
        assertThat(llmPort.invocationCount()).isEqualTo(1);
        assertThat(countriesPort.invocationCount()).isZero();
        assertThat(weatherPort.invocationCount()).isZero();
        assertThat(turn.answer().sources()).hasSize(2);
        assertThat(turn.answer().sources().get(0)).isInstanceOf(AnswerSource.RagKnowledge.class);
        assertThat(turn.answer().sources().get(1)).isInstanceOf(AnswerSource.ModelSynthesis.class);
    }

    @Test
    void ragNoResultReportsInsufficientProductKnowledgeWithoutLlm() {
        ragKnowledgePort.onRetrieve((question, policy) -> new RagRetrievalResult.NoRelevantKnowledge());

        ConversationTurn turn = answer(UserQuestion.of("What is CDQ Fraud Guard?"));

        assertThat(ragKnowledgePort.retrieveInvocationCount()).isEqualTo(1);
        assertThat(llmPort.invocationCount()).isZero();
        assertThat(turn.answer().answerText()).contains("insufficient product knowledge");
        assertThat(((AnswerSource.RagKnowledge) turn.answer().sources().getFirst()).status())
                .isEqualTo(SourceContributionStatus.INSUFFICIENT);
    }

    @Test
    void ollamaUnavailableWhenSynthesisRequired() {
        countriesPort.register("Berlin", new ToolExecutionResult.Success<>(GERMANY));
        llmPort =
                new StubLlmPort(
                        new LlmResult.SourceUnavailable(
                                "Ollama chat", "connection refused", "start Ollama"));
        useCase =
                new AnswerQuestionUseCase(
                        countriesPort,
                        weatherPort,
                        ragKnowledgePort,
                        llmPort,
                        new SourceRoutingPolicy(),
                        new ResponseComposer(),
                        new RagRetrievalPolicy(5, 0.5));

        ConversationTurn turn = answer(UserQuestion.of("What do you know about Berlin?"));

        assertThat(llmPort.invocationCount()).isEqualTo(1);
        assertThat(turn.answer().answerText()).contains("Ollama chat is unavailable");
        assertThat(((AnswerSource.ModelSynthesis) turn.answer().sources().get(1)).status())
                .isEqualTo(SourceContributionStatus.UNAVAILABLE);
    }

    @Test
    void countriesUnavailableBlocksWeatherInCombinedPath() {
        countriesPort.register(
                "Germany",
                new ToolExecutionResult.SourceUnavailable<>(
                        "countries MCP", "countries service down", "retry later"));

        ConversationTurn turn =
                answer(
                        UserQuestion.of("What is the temperature of the capital of Germany currently?"));

        assertThat(countriesPort.invocationCount()).isEqualTo(1);
        assertThat(weatherPort.invocationCount()).isZero();
        assertThat(turn.answer().answerText()).contains("Countries MCP is unavailable");
        assertThat(turn.answer().sources()).singleElement().isInstanceOf(AnswerSource.CountriesFacts.class);
    }

    @Test
    void countriesOkWeatherFailYieldsPartialAnswer() {
        countriesPort.register("Germany", new ToolExecutionResult.Success<>(GERMANY));
        weatherPort.register(
                "Berlin",
                new ToolExecutionResult.SourceUnavailable<>(
                        "weather MCP", "weather service down", "retry later"));

        ConversationTurn turn =
                answer(
                        UserQuestion.of("What is the temperature of the capital of Germany currently?"));

        assertThat(countriesPort.invocationCount()).isEqualTo(1);
        assertThat(weatherPort.invocationCount()).isEqualTo(1);
        AssistantAnswer answer = turn.answer();
        assertThat(answer.answerText()).contains("capital of Germany is Berlin");
        assertThat(answer.sources()).hasSize(2);
        assertThat(((AnswerSource.CountriesFacts) answer.sources().get(0)).status())
                .isEqualTo(SourceContributionStatus.USED);
        assertThat(((AnswerSource.WeatherObservation) answer.sources().get(1)).status())
                .isEqualTo(SourceContributionStatus.UNAVAILABLE);
    }

    private ConversationTurn answer(UserQuestion question) {
        RecordingAssistantResponseSink sink = new RecordingAssistantResponseSink();
        ConversationTurn turn = useCase.answer(question, sink);
        assertThat(sink.completedTurn()).isEqualTo(turn);
        return turn;
    }
}
