package dev.localassistant.assistant.adapters.inbound.http;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeSnippetResponse(
        String chunkText,
        String sourceUrl,
        String contentHash,
        int chunkIndex,
        Double retrievalSimilarityScore) {}
