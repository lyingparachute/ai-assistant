package dev.localassistant.countries.application;

import dev.localassistant.countries.model.CountryLookupOutcome;
import dev.localassistant.countries.model.LookupPlace;

public final class CountriesApplicationService {

    private final LookupCountryUseCase lookupCountryUseCase;

    public CountriesApplicationService(LookupCountryUseCase lookupCountryUseCase) {
        this.lookupCountryUseCase = lookupCountryUseCase;
    }

    public CountryLookupOutcome lookupCountry(String name) {
        return lookupCountryUseCase.lookup(LookupPlace.of(name));
    }
}
