package dev.localassistant.countries.adapters.outbound.restcountries;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.countries.config.CountriesMcpConfiguration;
import dev.localassistant.countries.model.CountryFacts;
import dev.localassistant.countries.model.LookupPlace;
import dev.localassistant.countries.ports.RestCountriesPort.RestCountriesQueryResult;
import dev.localassistant.countries.support.StubRestCountriesServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestCountriesHttpAdapterTest {

    private static final String UNAUTHORIZED_BODY = "{\"errors\":[{\"message\":\"Authorization key required.\"}]}";
    private static final String MALFORMED_JSON_BODY = "{\"data\":{\"objects\":";
    private static final String WRONG_SHAPE_BODY = "{\"data\":{\"objects\":{}}}";
    private static final String EXPECTED_ARRAY_REASON = "expected data.objects array";

    private StubRestCountriesServer stub;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        stub = new StubRestCountriesServer();
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    void nameLookupReturnsSuccessAndPicksPrimaryCapital() throws Exception {
        stub.stubNameLookup(
                "Germany",
                HttpStatus.OK.value(),
                StubRestCountriesServer.readFixture("/fixtures/restcountries/germany-by-name.json")
        );

        RestCountriesQueryResult result = adapterWithKey("test-api-key").findByName(LookupPlace.of("Germany"));

        assertThat(result).isInstanceOf(RestCountriesQueryResult.Success.class);
        CountryFacts facts = ((RestCountriesQueryResult.Success) result).countries().getFirst();
        assertThat(facts.countryName()).isEqualTo("Germany");
        assertThat(facts.capital()).isEqualTo("Berlin");
        assertThat(facts.region()).isEqualTo("Europe");
        assertThat(facts.population()).isEqualTo(83_240_525L);
    }

    @Test
    void nameLookupFallsBackToFirstCapitalWhenNoneMarkedPrimary() throws Exception {
        stub.stubNameLookup(
                "Bolivia",
                HttpStatus.OK.value(),
                StubRestCountriesServer.readFixture("/fixtures/restcountries/no-primary-capital.json")
        );

        RestCountriesQueryResult result = adapterWithKey("test-api-key").findByName(LookupPlace.of("Bolivia"));

        assertThat(result).isInstanceOf(RestCountriesQueryResult.Success.class);
        CountryFacts facts = ((RestCountriesQueryResult.Success) result).countries().getFirst();
        assertThat(facts.countryName()).isEqualTo("Bolivia");
        assertThat(facts.capital()).isEqualTo("Sucre");
    }

    @Test
    void emptyObjectsArrayReturnsNotFound() {
        stub.stubNameLookup("Atlantis", HttpStatus.OK.value(), StubRestCountriesServer.EMPTY_BODY);

        RestCountriesQueryResult result = adapterWithKey("test-api-key").findByName(LookupPlace.of("Atlantis"));

        assertThat(result).isInstanceOf(RestCountriesQueryResult.NotFound.class);
    }

    @Test
    void unauthorizedResponseReturnsSourceUnavailableWithAuthHint() {
        stub.stubNameLookup("Germany", HttpStatus.UNAUTHORIZED.value(), UNAUTHORIZED_BODY);

        RestCountriesQueryResult result = adapterWithKey("wrong-key").findByName(LookupPlace.of("Germany"));

        assertThat(result).isInstanceOf(RestCountriesQueryResult.SourceUnavailable.class);
        assertThat(((RestCountriesQueryResult.SourceUnavailable) result).reason())
                .contains("REST_COUNTRIES_API_KEY");
    }

    @Test
    void blankApiKeyReturnsSourceUnavailableWithoutCallingApi() {
        RestCountriesQueryResult result = adapterWithKey("").findByName(LookupPlace.of("Germany"));

        assertThat(result).isInstanceOf(RestCountriesQueryResult.SourceUnavailable.class);
        assertThat(((RestCountriesQueryResult.SourceUnavailable) result).reason())
                .contains("REST_COUNTRIES_API_KEY");
        assertThat(stub.requestCount()).isZero();
    }

    @Test
    void malformedJsonBodyReturnsSourceUnavailable() {
        stub.stubNameLookup("Germany", HttpStatus.OK.value(), MALFORMED_JSON_BODY);

        RestCountriesQueryResult result = adapterWithKey("test-api-key").findByName(LookupPlace.of("Germany"));

        assertThat(result).isInstanceOf(RestCountriesQueryResult.SourceUnavailable.class);
    }

    @Test
    void wrongShapeBodyReturnsSourceUnavailable() {
        stub.stubNameLookup("Germany", HttpStatus.OK.value(), WRONG_SHAPE_BODY);

        RestCountriesQueryResult result = adapterWithKey("test-api-key").findByName(LookupPlace.of("Germany"));

        assertThat(result).isInstanceOf(RestCountriesQueryResult.SourceUnavailable.class);
        assertThat(((RestCountriesQueryResult.SourceUnavailable) result).reason())
                .isEqualTo(EXPECTED_ARRAY_REASON);
    }

    @Test
    void interruptedSendReturnsSourceUnavailableAndRestoresInterruptFlag() throws Exception {
        HttpClient interruptingClient = mock(HttpClient.class);
        when(interruptingClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new InterruptedException("send interrupted"));
        CountriesMcpConfiguration configuration = new CountriesMcpConfiguration(
                stub.baseUrl(),
                "test-api-key",
                2,
                "countries-mcp-server-test",
                "test",
                20
        );
        RestCountriesHttpAdapter adapter =
                new RestCountriesHttpAdapter(configuration, interruptingClient, objectMapper);

        RestCountriesQueryResult result = adapter.findByName(LookupPlace.of("Germany"));

        assertThat(result).isInstanceOf(RestCountriesQueryResult.SourceUnavailable.class);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void transportFailureReturnsSourceUnavailable() {
        CountriesMcpConfiguration configuration = new CountriesMcpConfiguration(
                "http://localhost:1",
                "test-api-key",
                1,
                "countries-mcp-server-test",
                "test",
                20
        );
        RestCountriesHttpAdapter adapter =
                new RestCountriesHttpAdapter(configuration, HttpClient.newHttpClient(), objectMapper);

        RestCountriesQueryResult result = adapter.findByName(LookupPlace.of("Germany"));

        assertThat(result).isInstanceOf(RestCountriesQueryResult.SourceUnavailable.class);
    }

    private RestCountriesHttpAdapter adapterWithKey(String apiKey) {
        CountriesMcpConfiguration configuration = new CountriesMcpConfiguration(
                stub.baseUrl(),
                apiKey,
                2,
                "countries-mcp-server-test",
                "test",
                20
        );
        return new RestCountriesHttpAdapter(configuration, HttpClient.newHttpClient(), objectMapper);
    }
}
