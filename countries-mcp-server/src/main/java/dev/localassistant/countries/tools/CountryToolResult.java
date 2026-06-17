package dev.localassistant.countries.tools;

import dev.localassistant.countries.model.CountryFacts;
import dev.localassistant.countries.model.CountryLookupOutcome;
import dev.localassistant.countries.support.errors.CountryToolErrors;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CountryToolResult {

    private CountryToolResult() {
    }

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

    public static Map<String, Object> fromOutcome(CountryLookupOutcome outcome) {
        return switch (outcome) {
            case CountryLookupOutcome.Success success -> success(success.facts());
            case CountryLookupOutcome.NotRecognized notRecognized ->
                    CountryToolErrors.envelope(
                            CountryToolErrors.ERROR_NOT_RECOGNIZED,
                            notRecognized.hint()
                    );
            case CountryLookupOutcome.AmbiguousCapital ambiguousCapital ->
                    CountryToolErrors.envelope(
                            CountryToolErrors.ERROR_AMBIGUOUS_CAPITAL,
                            ambiguousCapital.hint()
                    );
            case CountryLookupOutcome.SourceUnavailable sourceUnavailable ->
                    CountryToolErrors.envelope(
                            CountryToolErrors.ERROR_SOURCE_UNAVAILABLE,
                            sourceUnavailable.hint()
                    );
        };
    }

    public static boolean isErrorOutcome(CountryLookupOutcome outcome) {
        return !(outcome instanceof CountryLookupOutcome.Success);
    }
}
