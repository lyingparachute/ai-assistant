package dev.localassistant.assistant.adapters.inbound.cli;

import dev.localassistant.assistant.config.AssistantRagProperties;
import dev.localassistant.assistant.rag.RagIngestionReport;
import dev.localassistant.assistant.rag.RagIngestionResult;
import dev.localassistant.assistant.rag.RagIngestionUseCase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Local setup entry point for CDQ Fraud Guard RAG ingestion.
 * <p>
 * Run with {@code --ingest-rag} or {@code ASSISTANT_INGEST_RAG=true} to extract product-page
 * content, chunk it, embed it, and store it in pgvector without starting chat orchestration.
 */
@Component
@Order(0)
@ConditionalOnBean(RagIngestionUseCase.class)
class RagIngestionCommand implements ApplicationRunner {

    private static final String INGEST_RAG_ARGUMENT = "ingest-rag";
    private static final String INGEST_RAG_ENV = "ASSISTANT_INGEST_RAG";

    private final ConfigurableApplicationContext applicationContext;
    private final RagIngestionUseCase ragIngestionUseCase;
    private final AssistantRagProperties assistantRagProperties;

    RagIngestionCommand(
            ConfigurableApplicationContext applicationContext,
            RagIngestionUseCase ragIngestionUseCase,
            AssistantRagProperties assistantRagProperties) {
        this.applicationContext = applicationContext;
        this.ragIngestionUseCase = ragIngestionUseCase;
        this.assistantRagProperties = assistantRagProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!shouldRunIngestion(args)) {
            return;
        }

        RagIngestionResult result = ragIngestionUseCase.ingest(assistantRagProperties.sourceUrl());
        printReport(result);
        int exitCode = result instanceof RagIngestionResult.Success ? 0 : 1;
        SpringApplication.exit(applicationContext, () -> exitCode);
    }

    private boolean shouldRunIngestion(ApplicationArguments args) {
        if (args.containsOption(INGEST_RAG_ARGUMENT)) {
            return true;
        }
        String envValue = System.getenv(INGEST_RAG_ENV);
        return envValue != null && Boolean.parseBoolean(envValue);
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
