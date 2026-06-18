package dev.localassistant.assistant.answering.api.http;

import jakarta.validation.constraints.NotBlank;

record ChatRequest(@NotBlank(message = "question must not be blank") String question) {
}
