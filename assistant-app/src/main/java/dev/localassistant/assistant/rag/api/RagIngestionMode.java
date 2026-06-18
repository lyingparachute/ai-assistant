package dev.localassistant.assistant.rag.api;

import lombok.experimental.UtilityClass;
import org.springframework.boot.ApplicationArguments;

import java.util.function.Function;

@UtilityClass
public class RagIngestionMode {

    private static final String INGEST_RAG_ARGUMENT = "ingest-rag";
    private static final String INGEST_RAG_ENV = "ASSISTANT_INGEST_RAG";
    public static final String PROFILE = "ingest-rag";

    public static boolean enabled(ApplicationArguments args) {
        return enabled(args, System::getenv);
    }

    static boolean enabled(ApplicationArguments args, Function<String, String> env) {
        return args.containsOption(INGEST_RAG_ARGUMENT) || envEnabled(env);
    }

    private static boolean envEnabled(Function<String, String> env) {
        final var envValue = env.apply(INGEST_RAG_ENV);
        return envValue != null && Boolean.parseBoolean(envValue);
    }
}
