package dev.localassistant.assistant.answering.domain;

public enum SourceType {
    COUNTRIES_FACTS,
    WEATHER_OBSERVATION,
    RAG_KNOWLEDGE,
    MODEL_SYNTHESIS;

    public static SourceType from(final AnswerSource source) {
        return switch (source) {
            case final AnswerSource.CountriesFacts ignored -> COUNTRIES_FACTS;
            case final AnswerSource.WeatherObservation ignored -> WEATHER_OBSERVATION;
            case final AnswerSource.RagKnowledge ignored -> RAG_KNOWLEDGE;
            case final AnswerSource.ModelSynthesis ignored -> MODEL_SYNTHESIS;
        };
    }
}
