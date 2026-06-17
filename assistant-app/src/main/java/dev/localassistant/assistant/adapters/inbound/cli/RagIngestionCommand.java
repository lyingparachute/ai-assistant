package dev.localassistant.assistant.adapters.inbound.cli;

import dev.localassistant.assistant.config.AssistantRagRetrievalProperties;
import dev.localassistant.assistant.rag.RagIngestionReport;
import dev.localassistant.assistant.rag.RagIngestionResult;
import dev.localassistant.assistant.rag.RagIngestionUseCase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;

/**
 * Local setup entry point for CDQ Fraud Guard RAG ingestion.
 * <p>
 * Run with {@code --ingest-rag} or {@code ASSISTANT_INGEST_RAG=true} to extract product-page
 * content, chunk it, embed it, and store it in pgvector without starting chat orchestration.
 */
@Order(0)
public class RagIngestionCommand implements ApplicationRunner {

    private final ConfigurableApplicationContext applicationContext;
    private final RagIngestionUseCase ragIngestionUseCase;
    private final AssistantRagRetrievalProperties retrievalProperties;

    public RagIngestionCommand(
            ConfigurableApplicationContext applicationContext,
            RagIngestionUseCase ragIngestionUseCase,
            AssistantRagRetrievalProperties retrievalProperties) {
        this.applicationContext = applicationContext;
        this.ragIngestionUseCase = ragIngestionUseCase;
        this.retrievalProperties = retrievalProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!shouldRunIngestion(args)) {
            return;
        }

        RagIngestionResult result = ragIngestionUseCase.ingest(retrievalProperties.sourceUrl());
        printReport(result);
        int exitCode = result instanceof RagIngestionResult.Success ? 0 : 1;
        SpringApplication.exit(applicationContext, () -> exitCode);
    }

    private boolean shouldRunIngestion(ApplicationArguments args) {
        return RagIngestionMode.enabled(args);
    }

    private void printReport(RagIngestionResult result) {
        if (result instanceof RagIngestionResult.SourceUnavailable unavailable) {
            System.out.printf(
                    "RAG ingestion failed%nsource: %s%nmessage: %s%nhint: %s%n",
                    unavailable.sourceLabel(), unavailable.message(), unavailable.hint());
            return;
        }

        RagIngestionReport report = ((RagIngestionResult.Success) result).report();
        System.out.printf(
                "RAG ingestion complete%nsource-url: %s%ncontent-hash: %s%nchunk-count: %d%noutcome: %s%n",
                report.sourceUrl(), report.contentHash(), report.chunkCount(), report.outcome());
    }
}
