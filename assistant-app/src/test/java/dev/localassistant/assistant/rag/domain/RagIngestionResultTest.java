package dev.localassistant.assistant.rag.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagIngestionResultTest {

    @Test
    void successWrapsReport() {
        RagIngestionReport report =
                new RagIngestionReport(
                        "https://example.com",
                        "hash-1",
                        3,
                        RagIngestionReport.Outcome.INGESTED);

        RagIngestionResult.Success success = new RagIngestionResult.Success(report);

        assertThat(success.report()).isEqualTo(report);
    }

    @Test
    void successRejectsNullReport() {
        assertThatThrownBy(() -> new RagIngestionResult.Success(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("report");
    }

    @Test
    void sourceUnavailableRejectsBlankSourceLabel() {
        assertThatThrownBy(
                        () -> new RagIngestionResult.SourceUnavailable(" ", "message", "hint"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
