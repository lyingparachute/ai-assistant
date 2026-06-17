package dev.localassistant.countries.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.countries.adapters.inbound.mcp.CountriesMcpServerAdapter;
import dev.localassistant.countries.adapters.outbound.restcountries.RestCountriesHttpAdapter;
import dev.localassistant.countries.application.LookupCountryUseCase;
import dev.localassistant.countries.core.CountriesMcpServerFactory;
import dev.localassistant.countries.ports.RestCountriesPort;
import dev.localassistant.countries.tools.CountryLookupTool;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CountriesMcpConfiguration.class)
class CountriesMcpBeansConfiguration {

    @Bean
    HttpClient restCountriesHttpClient(CountriesMcpConfiguration configuration) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(configuration.restCountriesTimeoutSeconds()))
                .build();
    }

    @Bean
    RestCountriesPort restCountriesPort(
            CountriesMcpConfiguration configuration,
            HttpClient restCountriesHttpClient,
            ObjectMapper objectMapper
    ) {
        return new RestCountriesHttpAdapter(configuration, restCountriesHttpClient, objectMapper);
    }

    @Bean
    LookupCountryUseCase lookupCountryUseCase(RestCountriesPort restCountriesPort) {
        return new LookupCountryUseCase(restCountriesPort);
    }

    @Bean
    CountryLookupTool countryLookupTool(LookupCountryUseCase lookupCountryUseCase, ObjectMapper objectMapper) {
        return new CountryLookupTool(lookupCountryUseCase, objectMapper);
    }

    @Bean
    CountriesMcpServerFactory countriesMcpServerFactory(
            CountriesMcpConfiguration configuration,
            CountryLookupTool countryLookupTool,
            ObjectMapper objectMapper
    ) {
        return new CountriesMcpServerFactory(configuration, countryLookupTool, objectMapper);
    }

    @Bean
    CountriesMcpServerAdapter countriesMcpServerAdapter(CountriesMcpServerFactory countriesMcpServerFactory) {
        return new CountriesMcpServerAdapter(countriesMcpServerFactory);
    }
}
