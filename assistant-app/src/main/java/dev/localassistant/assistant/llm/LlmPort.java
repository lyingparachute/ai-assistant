package dev.localassistant.assistant.llm;

public interface LlmPort {

    LlmResult generate(PromptContext context, TokenSink tokenSink);
}
