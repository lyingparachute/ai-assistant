package dev.localassistant.assistant.rag.api;

import dev.localassistant.assistant.rag.domain.RagIngestionResult;
import dev.localassistant.assistant.rag.domain.port.inbound.IngestRag;
import dev.localassistant.assistant.rag.infrastructure.config.AssistantRagRetrievalProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Local setup entry point for CDQ Fraud Guard RAG ingestion.
 * <p>
 * Run with {@code --ingest-rag} or {@code ASSISTANT_INGEST_RAG=true} to extract product-page
 * content, chunk it, embed it, and store it in pgvector without starting chat orchestration.
 */
@Component
@Profile("ingest-rag")
@Order(0)
@RequiredArgsConstructor
class RagIngestionCli implements ApplicationRunner {

    private final ConfigurableApplicationContext applicationContext;
    private final IngestRag ingestRag;
    private final AssistantRagRetrievalProperties retrievalProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!RagIngestionMode.enabled(args)) {
            return;
        }

        final var result = ingestRag.execute(new IngestRag.Command(retrievalProperties.sourceUrl()));
        printReport(result);
        final var exitCode = result instanceof RagIngestionResult.Success ? 0 : 1;
        SpringApplication.exit(applicationContext, () -> exitCode);
    }

    private void printReport(RagIngestionResult result) {
        switch (result) {
            case RagIngestionResult.SourceUnavailable(var unavailability) ->
                    System.out.printf(
                            "RAG ingestion failed%nsource: %s%nmessage: %s%nhint: %s%n",
                            unavailability.sourceLabel(),
                            unavailability.message(),
                            unavailability.hint());
            case RagIngestionResult.Success(var report) ->
                    System.out.printf(
                            "RAG ingestion complete%nsource-url: %s%ncontent-hash: %s%nchunk-count: %d%noutcome: %s%n",
                            report.sourceUrl(),
                            report.contentHash(),
                            report.chunkCount(),
                            report.outcome());
        }
    }
}
