package dev.localassistant.countries.support.errors;

import java.util.List;
import java.util.Map;

public final class CountryToolErrors {

    public static final String ERROR_NAME_REQUIRED = "name is required";
    public static final String ERROR_NOT_RECOGNIZED = "country name is not recognized";
    public static final String ERROR_AMBIGUOUS_CAPITAL = "capital city matches more than one country";
    public static final String ERROR_SOURCE_UNAVAILABLE = "REST Countries source unavailable";
    public static final String ERROR_INTERNAL_FAILURE = "country lookup failed";

    public static final String HINT_NAME_REQUIRED =
            "Provide a non-empty English country name or capital city.";
    public static final String HINT_NOT_RECOGNIZED =
            "Provide an English country name or capital city such as Germany or Berlin.";
    public static final String HINT_SOURCE_UNAVAILABLE =
            "REST Countries is unavailable. Retry the lookup later and do not invent country facts.";
    public static final String HINT_INTERNAL_FAILURE =
            "Retry the lookup later. If the problem persists, check server logs on stderr.";

    private CountryToolErrors() {
    }

    public static Map<String, Object> nameRequired() {
        return envelope(ERROR_NAME_REQUIRED, HINT_NAME_REQUIRED);
    }

    public static Map<String, Object> notRecognized() {
        return envelope(ERROR_NOT_RECOGNIZED, HINT_NOT_RECOGNIZED);
    }

    public static Map<String, Object> ambiguousCapital(List<String> countryNames) {
        return envelope(
                ERROR_AMBIGUOUS_CAPITAL,
                dev.localassistant.countries.application.CountryLookupHints.ambiguousCapital(countryNames)
        );
    }

    public static Map<String, Object> sourceUnavailable() {
        return envelope(ERROR_SOURCE_UNAVAILABLE, HINT_SOURCE_UNAVAILABLE);
    }

    public static Map<String, Object> internalFailure() {
        return envelope(ERROR_INTERNAL_FAILURE, HINT_INTERNAL_FAILURE);
    }

    public static Map<String, Object> envelope(String error, String hint) {
        return Map.of("ok", false, "error", error, "hint", hint);
    }
}
