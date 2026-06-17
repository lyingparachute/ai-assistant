package dev.localassistant.countries.adapters.outbound.restcountries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.countries.config.CountriesMcpConfiguration;
import dev.localassistant.countries.model.CountryFacts;
import dev.localassistant.countries.model.LookupPlace;
import dev.localassistant.countries.ports.RestCountriesPort;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class RestCountriesHttpAdapter implements RestCountriesPort {

    private static final String FIELDS_QUERY = "fields=name,capital,region,population";

    private final CountriesMcpConfiguration configuration;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RestCountriesHttpAdapter(
            CountriesMcpConfiguration configuration,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.configuration = configuration;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public RestCountriesQueryResult findByName(LookupPlace place) {
        return query("/name/" + encodePathSegment(place.value()));
    }

    @Override
    public RestCountriesQueryResult findByCapital(LookupPlace place) {
        return query("/capital/" + encodePathSegment(place.value()));
    }

    private RestCountriesQueryResult query(String path) {
        URI uri = URI.create(trimTrailingSlash(configuration.restCountriesBaseUrl()) + path + "?" + FIELDS_QUERY);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(configuration.restCountriesTimeoutSeconds()))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return mapResponse(response.statusCode(), response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new RestCountriesQueryResult.SourceUnavailable(exception.getMessage());
        }
    }

    private RestCountriesQueryResult mapResponse(int statusCode, String body) {
        if (statusCode == 404) {
            return new RestCountriesQueryResult.NotFound();
        }
        if (statusCode < 200 || statusCode >= 300) {
            return new RestCountriesQueryResult.SourceUnavailable("HTTP " + statusCode);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) {
                return new RestCountriesQueryResult.SourceUnavailable("expected JSON array");
            }
            if (root.isEmpty()) {
                return new RestCountriesQueryResult.NotFound();
            }
            List<CountryFacts> countries = new ArrayList<>();
            for (JsonNode node : root) {
                mapCountry(node).ifPresent(countries::add);
            }
            if (countries.isEmpty()) {
                return new RestCountriesQueryResult.SourceUnavailable("missing required country fields");
            }
            return new RestCountriesQueryResult.Success(List.copyOf(countries));
        } catch (IOException exception) {
            return new RestCountriesQueryResult.SourceUnavailable(exception.getMessage());
        }
    }

    private java.util.Optional<CountryFacts> mapCountry(JsonNode node) {
        JsonNode nameNode = node.path("name");
        String countryName = nameNode.path("common").asText(null);
        JsonNode capitalNode = node.path("capital");
        String capital = capitalNode.isArray() && !capitalNode.isEmpty()
                ? capitalNode.get(0).asText(null)
                : null;
        String region = node.path("region").asText(null);
        if (countryName == null || capital == null || region == null || !node.hasNonNull("population")) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new CountryFacts(countryName, capital, region, node.path("population").asLong()));
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
