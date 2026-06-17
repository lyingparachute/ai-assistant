package dev.localassistant.countries.application;

public final class CountryLookupHints {

    public static final String NOT_RECOGNIZED =
            "Provide an English country name or capital city such as Germany or Berlin.";
    public static final String SOURCE_UNAVAILABLE =
            "REST Countries is unavailable. Retry the lookup later and do not invent country facts.";

    private CountryLookupHints() {
    }

    public static String ambiguousCapital(java.util.List<String> countryNames) {
        return "Capital matches multiple countries: "
                + String.join(", ", countryNames)
                + ". Provide the country name instead.";
    }
}
