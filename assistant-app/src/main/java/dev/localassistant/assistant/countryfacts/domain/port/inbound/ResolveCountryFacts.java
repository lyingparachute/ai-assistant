package dev.localassistant.assistant.countryfacts.domain.port.inbound;

import dev.localassistant.assistant.countryfacts.domain.CountryInfo;
import dev.localassistant.assistant.shared.ToolExecutionResult;

import java.util.Objects;

public interface ResolveCountryFacts {

    ToolExecutionResult<CountryInfo> execute(Command command);

    record Command(String name) {
        public Command {
            Objects.requireNonNull(name, "name");
            name = name.strip();
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }
}
