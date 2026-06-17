package dev.localassistant.countries.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "countries.mcp")
public record CountriesMcpConfiguration(
        @NotBlank String restCountriesBaseUrl,
        @Positive int restCountriesTimeoutSeconds,
        @NotBlank String serverName,
        @NotBlank String serverVersion
) {

    public CountriesMcpConfiguration {
        restCountriesBaseUrl = restCountriesBaseUrl.strip();
        serverName = serverName.strip();
        serverVersion = serverVersion.strip();
    }
}
