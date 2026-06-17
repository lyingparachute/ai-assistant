package dev.localassistant.assistant.adapters.inbound.cli;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class RagIngestionModeTest {

    private static final String INGEST_RAG_ENV = "ASSISTANT_INGEST_RAG";
    private static final Function<String, String> EMPTY_ENV = name -> null;

    @Test
    void enabledWhenIngestRagArgumentPresent() {
        assertThat(RagIngestionMode.enabled(new DefaultApplicationArguments("--ingest-rag"))).isTrue();
    }

    @Test
    void disabledWhenNoFlagOrEnv() {
        assertThat(RagIngestionMode.enabled(new DefaultApplicationArguments(), EMPTY_ENV)).isFalse();
    }

    @Test
    void enabledWhenEnvVariableIsTrue() {
        boolean enabled =
                RagIngestionMode.enabled(
                        new DefaultApplicationArguments(), env(Map.of(INGEST_RAG_ENV, "true")));

        assertThat(enabled).isTrue();
    }

    @Test
    void disabledWhenEnvVariableIsFalse() {
        boolean enabled =
                RagIngestionMode.enabled(
                        new DefaultApplicationArguments(), env(Map.of(INGEST_RAG_ENV, "false")));

        assertThat(enabled).isFalse();
    }

    @Test
    void disabledWhenEnvVariableIsAbsent() {
        assertThat(RagIngestionMode.enabled(new DefaultApplicationArguments(), EMPTY_ENV)).isFalse();
    }

    @Test
    void argumentWinsEvenWhenEnvIsAbsent() {
        boolean enabled =
                RagIngestionMode.enabled(
                        new DefaultApplicationArguments("--ingest-rag"), EMPTY_ENV);

        assertThat(enabled).isTrue();
    }

    private static Function<String, String> env(Map<String, String> values) {
        return values::get;
    }
}
