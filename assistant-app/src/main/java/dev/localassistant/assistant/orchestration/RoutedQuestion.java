package dev.localassistant.assistant.orchestration;

import dev.localassistant.assistant.question.UserQuestion;
import dev.localassistant.assistant.tools.Location;
import java.util.Objects;
import java.util.Optional;

public record RoutedQuestion(
        UserQuestion question,
        QuestionRoute route,
        Optional<String> placeName,
        Optional<Location> weatherLocation,
        Optional<String> countryLookupKey,
        Optional<String> unsupportedReason) {

    public RoutedQuestion {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(placeName, "placeName");
        Objects.requireNonNull(weatherLocation, "weatherLocation");
        Objects.requireNonNull(countryLookupKey, "countryLookupKey");
        Objects.requireNonNull(unsupportedReason, "unsupportedReason");
        validateRouteFields(route, placeName, weatherLocation, countryLookupKey);
    }

    static RoutedQuestion countryCapital(UserQuestion question, String countryLookupKey) {
        return new RoutedQuestion(
                question,
                QuestionRoute.COUNTRY_CAPITAL,
                Optional.empty(),
                Optional.empty(),
                Optional.of(countryLookupKey),
                Optional.empty());
    }

    static RoutedQuestion countryThenWeather(UserQuestion question, String countryLookupKey) {
        return new RoutedQuestion(
                question,
                QuestionRoute.COUNTRY_THEN_WEATHER,
                Optional.empty(),
                Optional.empty(),
                Optional.of(countryLookupKey),
                Optional.empty());
    }

    static RoutedQuestion weatherLocation(UserQuestion question, Location location) {
        return new RoutedQuestion(
                question,
                QuestionRoute.WEATHER_LOCATION,
                Optional.empty(),
                Optional.of(location),
                Optional.empty(),
                Optional.empty());
    }

    static RoutedQuestion placeSynthesis(UserQuestion question, String placeName) {
        return new RoutedQuestion(
                question,
                QuestionRoute.PLACE_SYNTHESIS,
                Optional.of(placeName),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    static RoutedQuestion cdqProduct(UserQuestion question) {
        return new RoutedQuestion(
                question,
                QuestionRoute.CDQ_PRODUCT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    static RoutedQuestion unsupported(UserQuestion question, String reason) {
        return new RoutedQuestion(
                question,
                QuestionRoute.UNSUPPORTED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(reason));
    }

    private static void validateRouteFields(
            QuestionRoute route,
            Optional<String> placeName,
            Optional<Location> weatherLocation,
            Optional<String> countryLookupKey) {
        switch (route) {
            case COUNTRY_CAPITAL, COUNTRY_THEN_WEATHER -> {
                if (countryLookupKey.isEmpty()) {
                    throw new IllegalArgumentException("countryLookupKey required for " + route);
                }
            }
            case WEATHER_LOCATION -> {
                if (weatherLocation.isEmpty()) {
                    throw new IllegalArgumentException("weatherLocation required for WEATHER_LOCATION");
                }
            }
            case PLACE_SYNTHESIS -> {
                if (placeName.isEmpty() || placeName.get().isBlank()) {
                    throw new IllegalArgumentException("placeName required for PLACE_SYNTHESIS");
                }
            }
            case CDQ_PRODUCT, UNSUPPORTED -> {
                // no required extracted fields
            }
        }
    }
}
