package dev.localassistant.assistant.orchestration;

import dev.localassistant.assistant.question.AnswerSource;

public enum SourceType {
    COUNTRIES_FACTS,
    WEATHER_OBSERVATION,
    RAG_KNOWLEDGE,
    MODEL_SYNTHESIS;

    public static SourceType from(AnswerSource source) {
        return switch (source) {
            case AnswerSource.CountriesFacts ignored -> COUNTRIES_FACTS;
            case AnswerSource.WeatherObservation ignored -> WEATHER_OBSERVATION;
            case AnswerSource.RagKnowledge ignored -> RAG_KNOWLEDGE;
            case AnswerSource.ModelSynthesis ignored -> MODEL_SYNTHESIS;
        };
    }
}
