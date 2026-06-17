package dev.localassistant.assistant.adapters.inbound.http;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(
        String answerText, List<SourceResponse> sources, String traceCorrelationId) {}
