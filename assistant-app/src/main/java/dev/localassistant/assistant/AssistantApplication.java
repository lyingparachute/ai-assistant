package dev.localassistant.assistant;

import dev.localassistant.assistant.adapters.inbound.cli.RagIngestionMode;
import dev.localassistant.assistant.config.OrchestrationConfiguration;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(OrchestrationConfiguration.class)
public class AssistantApplication {

    public static void main(String[] args) {
        final var applicationArguments = new DefaultApplicationArguments(args);
        if (RagIngestionMode.enabled(applicationArguments)) {
            var application = new SpringApplication(AssistantApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setAdditionalProfiles(RagIngestionMode.PROFILE);
            application.run(args);
            return;
        }
        SpringApplication.run(AssistantApplication.class, args);
    }
}
