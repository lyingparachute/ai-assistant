package dev.localassistant.assistant.adapters.inbound.http;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank(message = "question must not be blank") String question) {}
