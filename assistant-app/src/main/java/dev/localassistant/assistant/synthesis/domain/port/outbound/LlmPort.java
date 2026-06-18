package dev.localassistant.assistant.synthesis.domain.port.outbound;

import dev.localassistant.assistant.synthesis.domain.LlmResult;
import dev.localassistant.assistant.synthesis.domain.PromptContext;
import dev.localassistant.assistant.synthesis.domain.TokenSink;

public interface LlmPort {

    LlmResult generate(PromptContext context, TokenSink tokenSink);
}
