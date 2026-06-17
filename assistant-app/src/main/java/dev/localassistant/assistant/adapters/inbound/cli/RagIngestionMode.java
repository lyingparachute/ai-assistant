package dev.localassistant.assistant.adapters.inbound.cli;

import org.springframework.boot.ApplicationArguments;

import java.util.function.Function;

public final class RagIngestionMode {

    private static final String INGEST_RAG_ARGUMENT = "ingest-rag";
    private static final String INGEST_RAG_ENV = "ASSISTANT_INGEST_RAG";
    public static final String PROFILE = "ingest-rag";

    private RagIngestionMode() {}

    public static boolean enabled(ApplicationArguments args) {
        return enabled(args, System::getenv);
    }

    static boolean enabled(ApplicationArguments args, Function<String, String> env) {
        return args.containsOption(INGEST_RAG_ARGUMENT) || envEnabled(env);
    }

    private static boolean envEnabled(Function<String, String> env) {
        String envValue = env.apply(INGEST_RAG_ENV);
        return envValue != null && Boolean.parseBoolean(envValue);
    }
}
