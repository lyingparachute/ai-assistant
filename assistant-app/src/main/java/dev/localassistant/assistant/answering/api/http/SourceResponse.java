package dev.localassistant.assistant.answering.api.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SourceResponse.CountriesFacts.class, name = "countries_facts"),
    @JsonSubTypes.Type(value = SourceResponse.WeatherObservation.class, name = "weather_observation"),
    @JsonSubTypes.Type(value = SourceResponse.RagKnowledge.class, name = "rag_knowledge"),
    @JsonSubTypes.Type(value = SourceResponse.ModelSynthesis.class, name = "model_synthesis")
})
sealed interface SourceResponse {

    String status();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CountriesFacts(String status, CountryInfoResponse countryInfo, String unavailableMessage, String unavailableHint)
        implements SourceResponse {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record WeatherObservation(
        String status,
        WeatherReportResponse weatherReport,
        String unavailableMessage,
        String unavailableHint)
        implements SourceResponse {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RagKnowledge(
        String status,
        List<KnowledgeSnippetResponse> snippets,
        String unavailableMessage,
        String unavailableHint)
        implements SourceResponse {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ModelSynthesis(String status, String unavailableMessage, String unavailableHint)
        implements SourceResponse {
    }
}
