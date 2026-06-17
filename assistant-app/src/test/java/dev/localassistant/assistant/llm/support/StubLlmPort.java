package dev.localassistant.assistant.llm.support;

import dev.localassistant.assistant.llm.LlmPort;
import dev.localassistant.assistant.llm.LlmResult;
import dev.localassistant.assistant.llm.PromptContext;

import java.util.Objects;
import java.util.function.Function;

public final class StubLlmPort implements LlmPort {

    private final Function<PromptContext, LlmResult> handler;
    private int invocationCount;

    public StubLlmPort(LlmResult fixedResult) {
        Objects.requireNonNull(fixedResult, "fixedResult");
        this.handler = context -> fixedResult;
    }

    public StubLlmPort(Function<PromptContext, LlmResult> handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public LlmResult generate(PromptContext context) {
        invocationCount++;
        return handler.apply(context);
    }

    public int invocationCount() {
        return invocationCount;
    }
}
