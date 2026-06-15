package dev.localassistant.countries;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class CountriesMcpServerApplicationTest {

    @Test
    void exposesSpringBootApplicationEntrypoint() {
        assertThat(CountriesMcpServerApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .isTrue();
    }
}
