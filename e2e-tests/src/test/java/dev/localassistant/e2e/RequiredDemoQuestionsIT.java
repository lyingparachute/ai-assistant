package dev.localassistant.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RequiredDemoQuestionsIT {

    private final DemoQuestions demoQuestions = DemoQuestions.load();

    private AssistantApiClient client;

    @BeforeAll
    void connectToRunningAssistant() {
        client = AssistantApiClient.tryConnect()
                .orElseThrow(() -> new IllegalStateException(
                        "assistant-app is not reachable on the configured base URL; "
                                + "start the assistant before running the demo verification (mvn verify -P e2e)"));
    }

    @Test
    void germanyCapitalReturnsStructuredCountriesSource() throws Exception {
        JsonNode response = client.chat(demoQuestions.question("germany-capital"));

        assertThat(response.path("answerText").asText()).isNotBlank();
        assertThat(response.path("traceCorrelationId").asText()).isNotBlank();
        assertThat(response.path("sources").isArray()).isTrue();
        assertThat(response.path("sources").size()).isGreaterThanOrEqualTo(1);

        JsonNode countriesSource = response.path("sources").get(0);
        assertThat(countriesSource.path("type").asText()).isEqualTo("countries_facts");
        assertThat(countriesSource.path("status").asText()).isIn("USED", "UNAVAILABLE");

        if ("USED".equals(countriesSource.path("status").asText())) {
            assertThat(countriesSource.path("countryInfo").path("capital").asText()).isEqualTo("Berlin");
            assertThat(countriesSource.path("countryInfo").path("countryName").asText())
                    .isEqualToIgnoringCase("Germany");
        }
    }

    @Test
    void munichWeatherReturnsStructuredWeatherSourceWithoutHardcodedTemperature() throws Exception {
        JsonNode response = client.chat(demoQuestions.question("munich-weather"));

        assertThat(response.path("answerText").asText()).isNotBlank();
        assertThat(response.path("traceCorrelationId").asText()).isNotBlank();

        JsonNode weatherSource = findSource(response, "weather_observation");
        assertThat(weatherSource.path("status").asText()).isIn("USED", "UNAVAILABLE");

        if ("USED".equals(weatherSource.path("status").asText())) {
            JsonNode report = weatherSource.path("weatherReport");
            assertThat(report.path("location").path("city").asText()).isEqualToIgnoringCase("Munich");
            assertThat(report.path("temperature").path("celsius").isNumber()).isTrue();
            assertThat(report.path("timestamp").path("kind").asText()).isIn("observed", "retrieved");
            assertThat(report.path("timestamp").path("value").asText()).isNotBlank();
        }
    }

    @Test
    void germanyCapitalWeatherReturnsCountriesAndWeatherSources() throws Exception {
        JsonNode response = client.chat(demoQuestions.question("germany-capital-weather"));

        assertThat(response.path("answerText").asText()).isNotBlank();
        assertThat(response.path("traceCorrelationId").asText()).isNotBlank();
        assertThat(response.path("sources").size()).isGreaterThanOrEqualTo(1);

        JsonNode countriesSource = findSource(response, "countries_facts");
        assertThat(countriesSource.path("type").asText()).isEqualTo("countries_facts");

        if ("USED".equals(countriesSource.path("status").asText())) {
            JsonNode weatherSource = findSource(response, "weather_observation");
            assertThat(weatherSource.path("type").asText()).isEqualTo("weather_observation");
        }
    }

    @Test
    void berlinPlaceQuestionReturnsCountriesAndOptionalModelSynthesis() throws Exception {
        JsonNode response = client.chat(demoQuestions.question("berlin-place"));

        assertThat(response.path("answerText").asText()).isNotBlank();
        assertThat(response.path("traceCorrelationId").asText()).isNotBlank();

        JsonNode countriesSource = findSource(response, "countries_facts");
        assertThat(countriesSource.path("type").asText()).isEqualTo("countries_facts");
        assertThat(countriesSource.path("status").asText()).isIn("USED", "UNAVAILABLE");

        assertThat(hasSourceType(response, "weather_observation")).isFalse();
        assertThat(hasSourceType(response, "rag_knowledge")).isFalse();
    }

    @Test
    void blankQuestionReturnsValidationError() throws Exception {
        int status = client.chatExpectingStatus("", 400);
        assertThat(status).isEqualTo(400);
    }

    private static JsonNode findSource(JsonNode response, String type) {
        for (JsonNode source : response.path("sources")) {
            if (type.equals(source.path("type").asText())) {
                return source;
            }
        }
        throw new AssertionError("Expected source type '" + type + "' in response sources: " + response.path("sources"));
    }

    private static boolean hasSourceType(JsonNode response, String type) {
        for (JsonNode source : response.path("sources")) {
            if (type.equals(source.path("type").asText())) {
                return true;
            }
        }
        return false;
    }
}
