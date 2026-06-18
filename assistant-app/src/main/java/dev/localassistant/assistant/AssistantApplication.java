package dev.localassistant.assistant;

import dev.localassistant.assistant.rag.api.RagIngestionMode;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AssistantApplication {

    public static void main(final String[] args) {
        final var applicationArguments = new DefaultApplicationArguments(args);
        if (RagIngestionMode.enabled(applicationArguments)) {
            final var application = new SpringApplication(AssistantApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setAdditionalProfiles(RagIngestionMode.PROFILE);
            application.run(args);
            return;
        }
        SpringApplication.run(AssistantApplication.class, args);
    }
}
