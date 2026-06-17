package dev.localassistant.assistant.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagIngestionReportTest {

    @Test
    void acceptsValidIngestedReport() {
        RagIngestionReport report =
                new RagIngestionReport(
                        "https://example.com",
                        "hash-1",
                        5,
                        RagIngestionReport.Outcome.INGESTED);

        assertThat(report.sourceUrl()).isEqualTo("https://example.com");
        assertThat(report.chunkCount()).isEqualTo(5);
        assertThat(report.outcome()).isEqualTo(RagIngestionReport.Outcome.INGESTED);
    }

    @Test
    void acceptsUnchangedReportWithExistingChunkCount() {
        RagIngestionReport report =
                new RagIngestionReport(
                        "https://example.com",
                        "hash-1",
                        3,
                        RagIngestionReport.Outcome.UNCHANGED);

        assertThat(report.outcome()).isEqualTo(RagIngestionReport.Outcome.UNCHANGED);
        assertThat(report.chunkCount()).isEqualTo(3);
    }

    @Test
    void rejectsZeroChunkCountForUnchangedOutcome() {
        assertThatThrownBy(
                        () ->
                                new RagIngestionReport(
                                        "https://example.com",
                                        "hash-1",
                                        0,
                                        RagIngestionReport.Outcome.UNCHANGED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsBlankSourceUrl() {
        assertThatThrownBy(
                        () ->
                                new RagIngestionReport(
                                        " ",
                                        "hash-1",
                                        1,
                                        RagIngestionReport.Outcome.INGESTED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankContentHash() {
        assertThatThrownBy(
                        () ->
                                new RagIngestionReport(
                                        "https://example.com",
                                        " ",
                                        1,
                                        RagIngestionReport.Outcome.INGESTED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeChunkCount() {
        assertThatThrownBy(
                        () ->
                                new RagIngestionReport(
                                        "https://example.com",
                                        "hash-1",
                                        -1,
                                        RagIngestionReport.Outcome.INGESTED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroChunkCountForIngestedOutcome() {
        assertThatThrownBy(
                        () ->
                                new RagIngestionReport(
                                        "https://example.com",
                                        "hash-1",
                                        0,
                                        RagIngestionReport.Outcome.INGESTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsZeroChunkCountForReplacedOutcome() {
        assertThatThrownBy(
                        () ->
                                new RagIngestionReport(
                                        "https://example.com",
                                        "hash-1",
                                        0,
                                        RagIngestionReport.Outcome.REPLACED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsNonZeroChunkCountForSkippedOutcome() {
        assertThatThrownBy(
                        () ->
                                new RagIngestionReport(
                                        "https://example.com",
                                        "hash-1",
                                        2,
                                        RagIngestionReport.Outcome.SKIPPED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero");
    }
}
