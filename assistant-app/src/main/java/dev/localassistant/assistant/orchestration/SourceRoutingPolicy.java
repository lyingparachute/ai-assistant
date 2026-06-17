package dev.localassistant.assistant.orchestration;

import dev.localassistant.assistant.question.UserQuestion;
import dev.localassistant.assistant.tools.Location;
import java.util.Locale;

public final class SourceRoutingPolicy {

    // Routing is deliberately scoped to the fixed demo questions: keyword matches gate Germany and
    // Munich only. Off-demo inputs (for example "capital of France") fall through to UNSUPPORTED
    // rather than answering from model memory.
    private static final String GERMANY_LOOKUP_KEY = "Germany";
    private static final String MUNICH_LOCATION = "Munich";
    private static final String PLACE_SYNTHESIS_PREFIX = "what do you know about";
    private static final String KEYWORD_TEMPERATURE = "temperature";
    private static final String KEYWORD_CAPITAL = "capital";
    private static final String KEYWORD_GERMANY = "germany";
    private static final String KEYWORD_MUNICH = "munich";
    private static final String KEYWORD_CDQ = "cdq";
    private static final String KEYWORD_FRAUD_GUARD = "fraud guard";
    private static final String UNSUPPORTED_REASON = "No matching source route for this question";

    public RoutedQuestion route(UserQuestion question) {
        String normalized = question.text().toLowerCase(Locale.ROOT);

        if (containsCountryThenWeather(normalized)) {
            return new RoutedQuestion.CountryThenWeather(question, GERMANY_LOOKUP_KEY);
        }
        if (containsCountryCapital(normalized)) {
            return new RoutedQuestion.CountryCapital(question, GERMANY_LOOKUP_KEY);
        }
        if (containsWeatherLocation(normalized)) {
            return new RoutedQuestion.WeatherOnly(question, Location.of(MUNICH_LOCATION));
        }
        if (startsWithPlaceSynthesisPrefix(normalized)) {
            return new RoutedQuestion.PlaceSynthesis(question, extractPlaceName(question.text()));
        }
        if (containsCdqProduct(normalized)) {
            return new RoutedQuestion.CdqProduct(question);
        }
        return new RoutedQuestion.Unsupported(question, UNSUPPORTED_REASON);
    }

    private static boolean containsCountryThenWeather(String normalized) {
        return normalized.contains(KEYWORD_TEMPERATURE)
                && normalized.contains(KEYWORD_CAPITAL)
                && normalized.contains(KEYWORD_GERMANY);
    }

    private static boolean containsCountryCapital(String normalized) {
        return normalized.contains(KEYWORD_CAPITAL) && normalized.contains(KEYWORD_GERMANY);
    }

    private static boolean containsWeatherLocation(String normalized) {
        return normalized.contains(KEYWORD_TEMPERATURE) && normalized.contains(KEYWORD_MUNICH);
    }

    private static boolean startsWithPlaceSynthesisPrefix(String normalized) {
        return normalized.startsWith(PLACE_SYNTHESIS_PREFIX);
    }

    private static boolean containsCdqProduct(String normalized) {
        return normalized.contains(KEYWORD_CDQ) || normalized.contains(KEYWORD_FRAUD_GUARD);
    }

    private static String extractPlaceName(String questionText) {
        String normalized = questionText.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(PLACE_SYNTHESIS_PREFIX)) {
            throw new IllegalStateException("place synthesis prefix expected");
        }
        return stripTrailingSentencePunctuation(
                questionText.substring(PLACE_SYNTHESIS_PREFIX.length()).trim());
    }

    private static String stripTrailingSentencePunctuation(String value) {
        int end = value.length();
        while (end > 0 && isSentencePunctuation(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end).trim();
    }

    private static boolean isSentencePunctuation(char character) {
        return character == '?' || character == '.' || character == '!';
    }
}
