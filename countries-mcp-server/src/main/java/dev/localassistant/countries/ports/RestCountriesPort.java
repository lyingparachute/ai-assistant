package dev.localassistant.countries.ports;

import dev.localassistant.countries.model.CountryFacts;
import dev.localassistant.countries.model.LookupPlace;

import java.util.List;

public interface RestCountriesPort {

    RestCountriesQueryResult findByName(LookupPlace place);

    RestCountriesQueryResult findByCapital(LookupPlace place);

    sealed interface RestCountriesQueryResult {

        record Success(List<CountryFacts> countries) implements RestCountriesQueryResult {
        }

        record NotFound() implements RestCountriesQueryResult {
        }

        record SourceUnavailable(String reason) implements RestCountriesQueryResult {
        }
    }
}
