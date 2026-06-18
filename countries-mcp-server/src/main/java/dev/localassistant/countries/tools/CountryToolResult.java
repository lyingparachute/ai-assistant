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
        var data = new LinkedHashMap<String, Object>();
        data.put("countryName", facts.countryName());
        data.put("capital", facts.capital());
        data.put("region", facts.region());
        data.put("population", facts.population());

        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("ok", true);
        envelope.put("data", data);
        return envelope;
    }

    public static ToolEnvelope fromOutcome(CountryLookupOutcome outcome) {
        return switch (outcome) {
            case CountryLookupOutcome.Success(var facts) -> new ToolEnvelope(success(facts), false);
            case CountryLookupOutcome.NotRecognized ignored ->
                    new ToolEnvelope(CountryToolErrors.notRecognized(), true);
            case CountryLookupOutcome.AmbiguousCapital(var countryNames) ->
                    new ToolEnvelope(CountryToolErrors.ambiguousCapital(countryNames), true);
            case CountryLookupOutcome.SourceUnavailable ignored ->
                    new ToolEnvelope(CountryToolErrors.sourceUnavailable(), true);
        };
    }

    record ToolEnvelope(Map<String, Object> payload, boolean isError) {
    }
}
