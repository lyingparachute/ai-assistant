package dev.localassistant.countries;

import dev.localassistant.countries.config.CountriesMcpConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CountriesMcpConfiguration.class)
public class CountriesMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CountriesMcpServerApplication.class, args);
    }
}
