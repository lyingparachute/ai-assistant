package dev.localassistant.countries.model;

import java.util.List;

public sealed interface CountryLookupOutcome {

    record Success(CountryFacts facts) implements CountryLookupOutcome {
    }

    record NotRecognized() implements CountryLookupOutcome {
    }

    record AmbiguousCapital(List<String> countryNames) implements CountryLookupOutcome {
    }

    record SourceUnavailable() implements CountryLookupOutcome {
    }
}
