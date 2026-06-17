package dev.localassistant.countries.application;

import dev.localassistant.countries.model.CountryFacts;
import dev.localassistant.countries.model.CountryLookupOutcome;
import dev.localassistant.countries.model.LookupPlace;
import dev.localassistant.countries.ports.RestCountriesPort;

import java.util.List;
import java.util.Optional;

public final class LookupCountryUseCase {

    private final RestCountriesPort restCountriesPort;

    public LookupCountryUseCase(RestCountriesPort restCountriesPort) {
        this.restCountriesPort = restCountriesPort;
    }

    public CountryLookupOutcome lookup(LookupPlace place) {
        RestCountriesPort.RestCountriesQueryResult byName = restCountriesPort.findByName(place);
        CountryLookupOutcome nameOutcome = resolveNameLookup(place, byName);
        if (!(nameOutcome instanceof CountryLookupOutcome.NotRecognized)) {
            return nameOutcome;
        }

        RestCountriesPort.RestCountriesQueryResult byCapital = restCountriesPort.findByCapital(place);
        return resolveCapitalLookup(byCapital);
    }

    private CountryLookupOutcome resolveNameLookup(
            LookupPlace place,
            RestCountriesPort.RestCountriesQueryResult byName
    ) {
        return switch (byName) {
            case RestCountriesPort.RestCountriesQueryResult.SourceUnavailable sourceUnavailable ->
                    new CountryLookupOutcome.SourceUnavailable(CountryLookupHints.SOURCE_UNAVAILABLE);
            case RestCountriesPort.RestCountriesQueryResult.NotFound ignored ->
                    new CountryLookupOutcome.NotRecognized(CountryLookupHints.NOT_RECOGNIZED);
            case RestCountriesPort.RestCountriesQueryResult.Success success -> {
                Optional<CountryFacts> selected = selectSingleCountry(place.value(), success.countries());
                if (selected.isPresent()) {
                    yield new CountryLookupOutcome.Success(selected.get());
                }
                yield new CountryLookupOutcome.NotRecognized(CountryLookupHints.NOT_RECOGNIZED);
            }
        };
    }

    private CountryLookupOutcome resolveCapitalLookup(RestCountriesPort.RestCountriesQueryResult byCapital) {
        return switch (byCapital) {
            case RestCountriesPort.RestCountriesQueryResult.SourceUnavailable sourceUnavailable ->
                    new CountryLookupOutcome.SourceUnavailable(CountryLookupHints.SOURCE_UNAVAILABLE);
            case RestCountriesPort.RestCountriesQueryResult.NotFound ignored ->
                    new CountryLookupOutcome.NotRecognized(CountryLookupHints.NOT_RECOGNIZED);
            case RestCountriesPort.RestCountriesQueryResult.Success success -> {
                List<CountryFacts> countries = success.countries();
                if (countries.isEmpty()) {
                    yield new CountryLookupOutcome.NotRecognized(CountryLookupHints.NOT_RECOGNIZED);
                }
                if (countries.size() == 1) {
                    yield new CountryLookupOutcome.Success(countries.getFirst());
                }
                List<String> countryNames = countries.stream().map(CountryFacts::countryName).toList();
                yield new CountryLookupOutcome.AmbiguousCapital(
                        countryNames,
                        CountryLookupHints.ambiguousCapital(countryNames)
                );
            }
        };
    }

    private Optional<CountryFacts> selectSingleCountry(String input, List<CountryFacts> countries) {
        if (countries.isEmpty()) {
            return Optional.empty();
        }
        if (countries.size() == 1) {
            return Optional.of(countries.getFirst());
        }
        return countries.stream()
                .filter(country -> country.countryName().equalsIgnoreCase(input))
                .findFirst();
    }
}
