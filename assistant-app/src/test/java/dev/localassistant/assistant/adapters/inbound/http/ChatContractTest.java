package dev.localassistant.assistant.adapters.inbound.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.assistant.question.AnswerSource;
import dev.localassistant.assistant.question.AssistantAnswer;
import dev.localassistant.assistant.question.ConversationTurn;
import dev.localassistant.assistant.question.UserQuestion;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.Location;
import dev.localassistant.assistant.tools.SourceUnavailability;
import dev.localassistant.assistant.tools.Temperature;
import dev.localassistant.assistant.tools.WeatherReport;
import dev.localassistant.assistant.tools.WeatherTimestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final CountryInfo GERMANY =
            new CountryInfo("Germany", "Berlin", "Europe", 83_240_525L);
    private static final WeatherReport MUNICH_WEATHER =
            new WeatherReport(
                    Location.of("Munich"),
                    Temperature.celsius(18.3),
                    new WeatherTimestamp.Retrieved(Instant.parse("2026-06-16T12:00:00Z")));

    private ChatHttpMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ChatHttpMapper();
    }

    @Test
    void chatRequestDeserializesQuestionField() throws Exception {
        ChatRequest request =
                OBJECT_MAPPER.readValue("{\"question\":\"Hello\"}", ChatRequest.class);

        assertThat(request.question()).isEqualTo("Hello");
    }

    @Test
    void chatResponseSerializesDiscriminatedSourcesAndTrace() throws Exception {
        AssistantAnswer answer =
                AssistantAnswer.withTrace(
                        "The capital of Germany is Berlin.",
                        List.of(
                                AnswerSource.CountriesFacts.used(GERMANY),
                                AnswerSource.WeatherObservation.used(MUNICH_WEATHER),
                                AnswerSource.ModelSynthesis.used()),
                        "trace-abc");
        ChatResponse response = mapper.toChatResponse(new ConversationTurn(UserQuestion.of("q"), answer));

        JsonNode json = OBJECT_MAPPER.valueToTree(response);

        assertThat(json.get("answerText").asText()).isEqualTo("The capital of Germany is Berlin.");
        assertThat(json.get("traceCorrelationId").asText()).isEqualTo("trace-abc");
        assertThat(json.get("sources")).hasSize(3);
        assertThat(json.get("sources").get(0).get("type").asText()).isEqualTo("countries_facts");
        assertThat(json.get("sources").get(0).get("status").asText()).isEqualTo("USED");
        assertThat(json.get("sources").get(0).get("countryInfo").get("capital").asText())
                .isEqualTo("Berlin");
        assertThat(json.get("sources").get(1).get("type").asText()).isEqualTo("weather_observation");
        assertThat(json.get("sources").get(1).get("weatherReport").get("timestamp").get("kind").asText())
                .isEqualTo("retrieved");
        assertThat(json.get("sources").get(2).get("type").asText()).isEqualTo("model_synthesis");
    }

    @Test
    void chatResponseOmitsTraceWhenAbsent() throws Exception {
        AssistantAnswer answer =
                AssistantAnswer.of(
                        "Unavailable.",
                        List.of(
                                AnswerSource.CountriesFacts.unavailable(
                                        new SourceUnavailability(
                                                "Countries MCP", "Countries MCP unavailable", "retry later"))));
        ChatResponse response = mapper.toChatResponse(new ConversationTurn(UserQuestion.of("q"), answer));

        JsonNode json = OBJECT_MAPPER.valueToTree(response);

        assertThat(json.has("traceCorrelationId")).isFalse();
        assertThat(json.get("sources").get(0).get("status").asText()).isEqualTo("UNAVAILABLE");
        assertThat(json.get("sources").get(0).get("unavailableMessage").asText())
                .isEqualTo("Countries MCP unavailable");
    }

    @Test
    void unavailableSourceEmitsMessageAndHintAndOmitsPayload() throws Exception {
        AssistantAnswer answer =
                AssistantAnswer.of(
                        "Countries MCP is unavailable: countries service down",
                        List.of(
                                AnswerSource.CountriesFacts.unavailable(
                                        new SourceUnavailability(
                                                "Countries MCP", "countries service down", "retry later"))));
        JsonNode json =
                OBJECT_MAPPER.valueToTree(
                        mapper.toChatResponse(new ConversationTurn(UserQuestion.of("q"), answer)));
        JsonNode source = json.get("sources").get(0);

        assertThat(source.get("type").asText()).isEqualTo("countries_facts");
        assertThat(source.get("status").asText()).isEqualTo("UNAVAILABLE");
        assertThat(source.get("unavailableMessage").asText()).isEqualTo("countries service down");
        assertThat(source.get("unavailableHint").asText()).isEqualTo("retry later");
        assertThat(source.has("countryInfo")).isFalse();
        assertThat(source.has("snippets")).isFalse();
    }

    @Test
    void ragKnowledgeSerializesSnippetsAndInsufficientStatus() throws Exception {
        AssistantAnswer used =
                AssistantAnswer.of(
                        "Product answer.",
                        List.of(
                                AnswerSource.RagKnowledge.used(
                                        List.of(
                                                KnowledgeSnippet.fromRetrieval(
                                                        "Fraud Guard monitors suppliers.",
                                                        "https://www.cdq.com/products/cdq-fraud-guard",
                                                        "hash",
                                                        0,
                                                        0.82)))));
        JsonNode usedJson = OBJECT_MAPPER.valueToTree(mapper.toChatResponse(
                new ConversationTurn(UserQuestion.of("q"), used)));

        assertThat(usedJson.get("sources").get(0).get("type").asText()).isEqualTo("rag_knowledge");
        assertThat(usedJson.get("sources").get(0).get("snippets")).hasSize(1);
        assertThat(usedJson.get("sources").get(0).get("snippets").get(0).get("retrievalSimilarityScore").asDouble())
                .isEqualTo(0.82);

        AssistantAnswer insufficient =
                AssistantAnswer.of(
                        "Insufficient knowledge.",
                        List.of(AnswerSource.RagKnowledge.insufficient()));
        JsonNode insufficientJson = OBJECT_MAPPER.valueToTree(mapper.toChatResponse(
                new ConversationTurn(UserQuestion.of("q"), insufficient)));

        assertThat(insufficientJson.get("sources").get(0).get("status").asText()).isEqualTo("INSUFFICIENT");
        assertThat(insufficientJson.get("sources").get(0).has("snippets")).isFalse();
    }
}
