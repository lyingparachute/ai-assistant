package dev.localassistant.assistant.synthesis.domain;

@FunctionalInterface
public interface TokenSink {

    void accept(String delta);

    static TokenSink discarding() {
        return delta -> {};
    }
}
