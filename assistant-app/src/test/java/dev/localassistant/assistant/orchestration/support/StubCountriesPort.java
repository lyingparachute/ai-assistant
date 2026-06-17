package dev.localassistant.assistant.orchestration.support;

import dev.localassistant.assistant.tools.CountriesPort;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.ToolExecutionResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class StubCountriesPort implements CountriesPort {

    private final Map<String, ToolExecutionResult<CountryInfo>> responses = new HashMap<>();
    private int invocationCount;

    public StubCountriesPort register(String name, ToolExecutionResult<CountryInfo> result) {
        responses.put(name, Objects.requireNonNull(result));
        return this;
    }

    @Override
    public ToolExecutionResult<CountryInfo> lookupCountry(String name) {
        invocationCount++;
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
