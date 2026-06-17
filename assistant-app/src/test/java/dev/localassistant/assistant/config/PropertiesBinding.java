package dev.localassistant.assistant.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.util.Map;

/**
 * Validated constructor-binding harness for {@code @ConfigurationProperties} record tests. Mirrors
 * how Spring Boot binds and validates the records at context startup without booting a context.
 */
final class PropertiesBinding {

    private PropertiesBinding() {
    }

    static <T> T bind(String prefix, Class<T> type, Map<String, Object> source) {
        Binder binder = new Binder(new MapConfigurationPropertySource(source));
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ValidationBindHandler handler = new ValidationBindHandler(new SpringValidatorAdapter(validator));
        return binder.bindOrCreate(prefix, Bindable.of(type), handler);
    }
}
