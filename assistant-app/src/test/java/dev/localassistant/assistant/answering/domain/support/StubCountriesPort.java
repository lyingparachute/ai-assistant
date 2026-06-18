package dev.localassistant.assistant.answering.domain.support;

import dev.localassistant.assistant.countryfacts.domain.CountryInfo;
import dev.localassistant.assistant.countryfacts.domain.port.inbound.ResolveCountryFacts;
import dev.localassistant.assistant.shared.ToolExecutionResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class StubCountriesPort implements ResolveCountryFacts {

    private final Map<String, ToolExecutionResult<CountryInfo>> responses = new HashMap<>();
    private int invocationCount;

    public StubCountriesPort register(String name, ToolExecutionResult<CountryInfo> result) {
        responses.put(name, Objects.requireNonNull(result));
        return this;
    }

    @Override
    public ToolExecutionResult<CountryInfo> execute(ResolveCountryFacts.Command command) {
        invocationCount++;
        final var name = command.name();
        ToolExecutionResult<CountryInfo> result = responses.get(name);
        if (result == null) {
            return new ToolExecutionResult.SourceUnavailable<>(
                    "Countries MCP", "No stub response for " + name, "Register a stub response");
        }
        return result;
    }

    public int invocationCount() {
        return invocationCount;
    }
}
