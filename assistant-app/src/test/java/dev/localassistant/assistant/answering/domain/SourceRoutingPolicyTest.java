package dev.localassistant.assistant.answering.domain;

import dev.localassistant.assistant.weather.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceRoutingPolicyTest {

    private SourceRoutingPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new SourceRoutingPolicy();
    }

    @Test
    void routesGermanyCapitalQuestion() {
        UserQuestion question = UserQuestion.of("What is the capital city of Germany?");

        RoutedQuestion routed = policy.route(question);

        assertThat(routed.route()).isEqualTo(QuestionRoute.COUNTRY_CAPITAL);
        assertThat(routed.question()).isEqualTo(question);
        assertThat(routed).isInstanceOfSatisfying(
                RoutedQuestion.CountryCapital.class,
                capital -> assertThat(capital.countryLookupKey()).isEqualTo("Germany"));
    }

    @Test
    void routesMunichTemperatureQuestion() {
        UserQuestion question = UserQuestion.of("What is the temperature currently in Munich?");

        RoutedQuestion routed = policy.route(question);

        assertThat(routed.route()).isEqualTo(QuestionRoute.WEATHER_LOCATION);
        assertThat(routed).isInstanceOfSatisfying(
                RoutedQuestion.WeatherOnly.class,
                weather -> assertThat(weather.weatherLocation()).isEqualTo(Location.of("Munich")));
    }

    @Test
    void routesGermanyCapitalTemperatureQuestion() {
        UserQuestion question =
                UserQuestion.of("What is the temperature of the capital of Germany currently?");

        RoutedQuestion routed = policy.route(question);

        assertThat(routed.route()).isEqualTo(QuestionRoute.COUNTRY_THEN_WEATHER);
        assertThat(routed).isInstanceOfSatisfying(
                RoutedQuestion.CountryThenWeather.class,
                chain -> assertThat(chain.countryLookupKey()).isEqualTo("Germany"));
    }

    @Test
    void routesBerlinPlaceSynthesisQuestion() {
        UserQuestion question = UserQuestion.of("What do you know about Berlin?");

        RoutedQuestion routed = policy.route(question);

        assertThat(routed.route()).isEqualTo(QuestionRoute.PLACE_SYNTHESIS);
        assertThat(routed).isInstanceOfSatisfying(
                RoutedQuestion.PlaceSynthesis.class,
                place -> assertThat(place.placeName()).isEqualTo("Berlin"));
    }

    @Test
    void routesCdqProductQuestion() {
        UserQuestion question = UserQuestion.of("What is CDQ Fraud Guard?");

        RoutedQuestion routed = policy.route(question);

        assertThat(routed.route()).isEqualTo(QuestionRoute.CDQ_PRODUCT);
        assertThat(routed).isInstanceOf(RoutedQuestion.CdqProduct.class);
    }

    @Test
    void routesFraudGuardKeywordAsCdqProduct() {
        UserQuestion question = UserQuestion.of("Tell me about fraud guard features");

        RoutedQuestion routed = policy.route(question);

        assertThat(routed.route()).isEqualTo(QuestionRoute.CDQ_PRODUCT);
    }

    @Test
    void countryCapitalRouteRequiresNoTemperatureKeyword() {
        UserQuestion capitalOnly = UserQuestion.of("What is the capital city of Germany?");
        UserQuestion combined =
                UserQuestion.of("What is the temperature of the capital of Germany currently?");

        assertThat(policy.route(capitalOnly).route()).isEqualTo(QuestionRoute.COUNTRY_CAPITAL);
        assertThat(policy.route(combined).route()).isEqualTo(QuestionRoute.COUNTRY_THEN_WEATHER);
    }

    @Test
    void offDemoCapitalQuestionFallsThroughToUnsupported() {
        UserQuestion question = UserQuestion.of("What is the capital of France?");

        RoutedQuestion routed = policy.route(question);

        assertThat(routed.route()).isEqualTo(QuestionRoute.UNSUPPORTED);
        assertThat(routed).isNotInstanceOf(RoutedQuestion.CountryCapital.class);
    }

    @Test
    void routesOffTopicQuestionAsUnsupported() {
        UserQuestion question = UserQuestion.of("Who won the World Cup in 2022?");

        RoutedQuestion routed = policy.route(question);

        assertThat(routed.route()).isEqualTo(QuestionRoute.UNSUPPORTED);
        assertThat(routed).isInstanceOfSatisfying(
                RoutedQuestion.Unsupported.class,
                unsupported ->
                        assertThat(unsupported.reason()).contains("No matching source route"));
    }
}
