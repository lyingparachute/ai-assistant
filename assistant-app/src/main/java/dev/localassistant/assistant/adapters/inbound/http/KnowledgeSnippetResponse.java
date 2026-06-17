package dev.localassistant.assistant.adapters.inbound.http;

import com.fasterxml.jackson.annotation.JsonInclude;

// Wire-decoupling DTO: deliberately mirrors the domain KnowledgeSnippet 1:1 so domain types stay
// off the HTTP contract and can evolve independently of the wire shape.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeSnippetResponse(
        String chunkText,
        String sourceUrl,
        String contentHash,
        int chunkIndex,
        Double retrievalSimilarityScore) {}
