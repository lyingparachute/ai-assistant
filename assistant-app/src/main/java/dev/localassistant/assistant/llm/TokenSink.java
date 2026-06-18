package dev.localassistant.assistant.llm;

@FunctionalInterface
public interface TokenSink {

    void accept(String delta);

    static TokenSink discarding() {
        return delta -> {};
    }
}
