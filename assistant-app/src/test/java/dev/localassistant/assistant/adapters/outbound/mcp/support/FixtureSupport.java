package dev.localassistant.assistant.adapters.outbound.mcp.support;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class FixtureSupport {

    private FixtureSupport() {
    }

    public static String readFixture(String classpathLocation) {
        try {
            return new ClassPathResource(classpathLocation).getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read fixture " + classpathLocation, exception);
        }
    }
}
