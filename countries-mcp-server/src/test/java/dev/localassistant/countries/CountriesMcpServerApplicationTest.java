package dev.localassistant.countries;

import dev.localassistant.countries.config.CountriesMcpConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CountriesMcpServerApplicationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues(
                    "countries.mcp.rest-countries-base-url=https://api.restcountries.com/countries/v5",
                    "countries.mcp.rest-countries-timeout-seconds=10",
                    "countries.mcp.server-name=countries-mcp-server",
                    "countries.mcp.server-version=0.1.0-SNAPSHOT",
                    "countries.mcp.stdio-enabled=false"
            );

    @Test
    void exposesSpringBootApplicationEntrypoint() {
        assertThat(CountriesMcpServerApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .isTrue();
    }

    @Test
    void rejectsBlankBaseUrlBeforeMcpStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestApplication.class)
                .withPropertyValues(
                        "countries.mcp.rest-countries-base-url=",
                        "countries.mcp.rest-countries-timeout-seconds=10",
                        "countries.mcp.server-name=countries-mcp-server",
                        "countries.mcp.server-version=0.1.0-SNAPSHOT",
                        "countries.mcp.stdio-enabled=false"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveTimeoutBeforeMcpStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestApplication.class)
                .withPropertyValues(
                        "countries.mcp.rest-countries-base-url=https://api.restcountries.com/countries/v5",
                        "countries.mcp.rest-countries-timeout-seconds=0",
                        "countries.mcp.server-name=countries-mcp-server",
                        "countries.mcp.server-version=0.1.0-SNAPSHOT",
                        "countries.mcp.stdio-enabled=false"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void loadsApplicationContextWithValidConfiguration() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @SpringBootApplication
    @EnableConfigurationProperties(CountriesMcpConfiguration.class)
    static class TestApplication {
    }
}
