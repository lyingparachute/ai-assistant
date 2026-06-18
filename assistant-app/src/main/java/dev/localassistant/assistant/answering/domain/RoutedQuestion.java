package dev.localassistant.assistant.answering.domain;

import dev.localassistant.assistant.weather.domain.Location;

import java.util.Objects;

sealed interface RoutedQuestion {

    UserQuestion question();

    QuestionRoute route();

    record CountryCapital(UserQuestion question, String countryLookupKey) implements RoutedQuestion {
        public CountryCapital {
            Objects.requireNonNull(question, "question");
            requireNonBlank(countryLookupKey, "countryLookupKey");
        }

        @Override
        public QuestionRoute route() {
            return QuestionRoute.COUNTRY_CAPITAL;
        }
    }

    record CountryThenWeather(UserQuestion question, String countryLookupKey)
        implements RoutedQuestion {
        public CountryThenWeather {
            Objects.requireNonNull(question, "question");
            requireNonBlank(countryLookupKey, "countryLookupKey");
        }

        @Override
        public QuestionRoute route() {
            return QuestionRoute.COUNTRY_THEN_WEATHER;
        }
    }

    record WeatherOnly(UserQuestion question, Location weatherLocation) implements RoutedQuestion {
        public WeatherOnly {
            Objects.requireNonNull(question, "question");
            Objects.requireNonNull(weatherLocation, "weatherLocation");
        }

        @Override
        public QuestionRoute route() {
            return QuestionRoute.WEATHER_LOCATION;
        }
    }

    record PlaceSynthesis(UserQuestion question, String placeName) implements RoutedQuestion {
        public PlaceSynthesis {
            Objects.requireNonNull(question, "question");
            requireNonBlank(placeName, "placeName");
        }

        @Override
        public QuestionRoute route() {
            return QuestionRoute.PLACE_SYNTHESIS;
        }
    }

    record CdqProduct(UserQuestion question) implements RoutedQuestion {
        public CdqProduct {
            Objects.requireNonNull(question, "question");
        }

        @Override
        public QuestionRoute route() {
            return QuestionRoute.CDQ_PRODUCT;
        }
    }

    record Unsupported(UserQuestion question, String reason) implements RoutedQuestion {
        public Unsupported {
            Objects.requireNonNull(question, "question");
            requireNonBlank(reason, "reason");
        }

        @Override
        public QuestionRoute route() {
            return QuestionRoute.UNSUPPORTED;
        }
    }

    private static void requireNonBlank(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
