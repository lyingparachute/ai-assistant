package dev.localassistant.assistant.answering.api.http;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
record ChatResponse(
    String answerText, List<SourceResponse> sources, String traceCorrelationId) {
}
