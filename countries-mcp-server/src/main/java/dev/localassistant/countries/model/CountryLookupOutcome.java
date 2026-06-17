package dev.localassistant.countries.model;

import java.util.List;

public sealed interface CountryLookupOutcome {

    record Success(CountryFacts facts) implements CountryLookupOutcome {
    }

    record NotRecognized(String hint) implements CountryLookupOutcome {
    }

    record AmbiguousCapital(List<String> countryNames, String hint) implements CountryLookupOutcome {
    }

    record SourceUnavailable(String hint) implements CountryLookupOutcome {
    }
}
