package dev.localassistant.countries.application;

import dev.localassistant.countries.model.CountryFacts;
import dev.localassistant.countries.model.CountryLookupOutcome;
import dev.localassistant.countries.model.LookupPlace;
import dev.localassistant.countries.ports.RestCountriesPort;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class LookupCountryUseCase {

    private final RestCountriesPort restCountriesPort;

    public LookupCountryUseCase(RestCountriesPort restCountriesPort) {
        this.restCountriesPort = restCountriesPort;
    }

    public CountryLookupOutcome lookup(LookupPlace place) {
        final var byName = restCountriesPort.findByName(place);
        final var nameOutcome = resolveNameLookup(place, byName);
        if (!(nameOutcome instanceof CountryLookupOutcome.NotRecognized)) {
            return nameOutcome;
        }

        final var byCapital = restCountriesPort.findByCapital(place);
        return resolveCapitalLookup(byCapital);
    }

    private CountryLookupOutcome resolveNameLookup(
            LookupPlace place,
            RestCountriesPort.RestCountriesQueryResult byName
    ) {
        return resolve(byName, success -> {
            final var selected = selectSingleCountry(place.value(), success.countries());
            return selected
                    .<CountryLookupOutcome>map(CountryLookupOutcome.Success::new)
                    .orElseGet(CountryLookupOutcome.NotRecognized::new);
        });
    }

    private CountryLookupOutcome resolveCapitalLookup(RestCountriesPort.RestCountriesQueryResult byCapital) {
        return resolve(byCapital, success -> {
            final var countries = success.countries();
            if (countries.isEmpty()) {
                return new CountryLookupOutcome.NotRecognized();
            }
            if (countries.size() == 1) {
                return new CountryLookupOutcome.Success(countries.getFirst());
            }
            final var countryNames = countries.stream().map(CountryFacts::countryName).toList();
            return new CountryLookupOutcome.AmbiguousCapital(countryNames);
        });
    }

    private CountryLookupOutcome resolve(
            RestCountriesPort.RestCountriesQueryResult result,
            Function<RestCountriesPort.RestCountriesQueryResult.Success, CountryLookupOutcome> onSuccess
    ) {
        return switch (result) {
            case RestCountriesPort.RestCountriesQueryResult.SourceUnavailable sourceUnavailable ->
                    new CountryLookupOutcome.SourceUnavailable();
            case RestCountriesPort.RestCountriesQueryResult.NotFound ignored ->
                    new CountryLookupOutcome.NotRecognized();
            case RestCountriesPort.RestCountriesQueryResult.Success success -> onSuccess.apply(success);
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
