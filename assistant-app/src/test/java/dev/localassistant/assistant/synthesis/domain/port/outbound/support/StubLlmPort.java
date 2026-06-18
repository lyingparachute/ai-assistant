package dev.localassistant.assistant.synthesis.domain.port.outbound.support;

import dev.localassistant.assistant.synthesis.domain.port.outbound.LlmPort;
import dev.localassistant.assistant.synthesis.domain.LlmResult;
import dev.localassistant.assistant.synthesis.domain.PromptContext;
import dev.localassistant.assistant.synthesis.domain.TokenSink;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class StubLlmPort implements LlmPort {

    private final BiFunction<PromptContext, TokenSink, LlmResult> handler;
    private int invocationCount;

    public StubLlmPort(LlmResult fixedResult) {
        Objects.requireNonNull(fixedResult, "fixedResult");
        this.handler = (context, tokenSink) -> fixedResult;
    }

    public StubLlmPort(Function<PromptContext, LlmResult> handler) {
        this.handler = (context, tokenSink) -> handler.apply(context);
    }

    public StubLlmPort(BiFunction<PromptContext, TokenSink, LlmResult> handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public LlmResult generate(PromptContext context, TokenSink tokenSink) {
        invocationCount++;
        return handler.apply(context, tokenSink);
    }

    public int invocationCount() {
        return invocationCount;
    }
}
