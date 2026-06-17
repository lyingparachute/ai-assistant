package dev.localassistant.assistant.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * RAG storage and retrieval settings.
 * <p>
 * The chunk table name is fixed to {@code rag_chunks} for M2; it is not configurable through
 * these properties.
 */
@Validated
@ConfigurationProperties(prefix = "assistant.rag")
public class AssistantRagProperties {

    @NotBlank
    private String jdbcUrl = "jdbc:postgresql://localhost:5432/assistant_rag";

    @NotBlank
    private String username = "assistant";

    @NotBlank
    private String password = "assistant";

    @Min(1)
    private int topK = 5;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double relevanceThreshold = 0.5;

    @NotBlank
    private String sourceUrl = "https://www.cdq.com/products/cdq-fraud-guard";

    @Min(1)
    private int chunkMaxSize = 1000;

    @Min(0)
    private int chunkOverlap = 200;

    @Min(1)
    private int fetchTimeoutSeconds = 30;

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String username() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String password() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int topK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double relevanceThreshold() {
        return relevanceThreshold;
    }

    public void setRelevanceThreshold(double relevanceThreshold) {
        this.relevanceThreshold = relevanceThreshold;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public int chunkMaxSize() {
        return chunkMaxSize;
    }

    public void setChunkMaxSize(int chunkMaxSize) {
        this.chunkMaxSize = chunkMaxSize;
    }

    public int chunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public int fetchTimeoutSeconds() {
        return fetchTimeoutSeconds;
    }

    public void setFetchTimeoutSeconds(int fetchTimeoutSeconds) {
        this.fetchTimeoutSeconds = fetchTimeoutSeconds;
    }
}
