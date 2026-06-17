package dev.localassistant.assistant.tools;

public interface CountriesPort {

    ToolExecutionResult<CountryInfo> lookupCountry(String name);
}
