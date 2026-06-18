package dev.localassistant.countries.tools;

import dev.localassistant.countries.model.CountryFacts;
import dev.localassistant.countries.model.CountryLookupOutcome;
import dev.localassistant.countries.support.errors.CountryToolErrors;
import lombok.experimental.UtilityClass;

import java.util.LinkedHashMap;
import java.util.Map;

@UtilityClass
public class CountryToolResult {

    public static Map<String, Object> success(CountryFacts facts) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("countryName", facts.countryName());
        data.put("capital", facts.capital());
        data.put("region", facts.region());
        data.put("population", facts.population());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("ok", true);
        envelope.put("data", data);
        return envelope;
    }

    public static ToolEnvelope fromOutcome(CountryLookupOutcome outcome) {
        return switch (outcome) {
            case CountryLookupOutcome.Success success -> new ToolEnvelope(success(success.facts()), false);
            case CountryLookupOutcome.NotRecognized ignored ->
                    new ToolEnvelope(CountryToolErrors.notRecognized(), true);
            case CountryLookupOutcome.AmbiguousCapital ambiguousCapital ->
                    new ToolEnvelope(CountryToolErrors.ambiguousCapital(ambiguousCapital.countryNames()), true);
            case CountryLookupOutcome.SourceUnavailable ignored ->
                    new ToolEnvelope(CountryToolErrors.sourceUnavailable(), true);
        };
    }

    record ToolEnvelope(Map<String, Object> payload, boolean isError) {
    }
}
