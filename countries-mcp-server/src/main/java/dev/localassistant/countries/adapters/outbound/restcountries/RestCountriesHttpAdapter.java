package dev.localassistant.countries.adapters.outbound.restcountries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.countries.config.CountriesMcpConfiguration;
import dev.localassistant.countries.model.CountryFacts;
import dev.localassistant.countries.model.LookupPlace;
import dev.localassistant.countries.ports.RestCountriesPort;
import org.apache.commons.lang3.StringUtils;

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
import java.util.Optional;

public final class RestCountriesHttpAdapter implements RestCountriesPort {

    private static final String NAME_PATH_PREFIX = "/names.common/";
    private static final String CAPITAL_PATH_PREFIX = "/capitals/";
    private static final String QUERY_SEPARATOR = "?";
    private static final String FIELDS_QUERY = "response_fields=names.common,capitals,region,population";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final String MISSING_API_KEY_REASON =
            "REST Countries API key is not configured; set REST_COUNTRIES_API_KEY";
    private static final String UNAUTHORIZED_REASON =
            "REST Countries rejected the API key (HTTP %d); check REST_COUNTRIES_API_KEY";

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
        return query(NAME_PATH_PREFIX + encodePathSegment(place.value()));
    }

    @Override
    public RestCountriesQueryResult findByCapital(LookupPlace place) {
        return query(CAPITAL_PATH_PREFIX + encodePathSegment(place.value()));
    }

    private RestCountriesQueryResult query(String path) {
        if (!configuration.hasRestCountriesApiKey()) {
            return new RestCountriesQueryResult.SourceUnavailable(MISSING_API_KEY_REASON);
        }
        URI uri = URI.create(
                trimTrailingSlash(configuration.restCountriesBaseUrl()) + path + QUERY_SEPARATOR + FIELDS_QUERY);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(configuration.restCountriesTimeoutSeconds()))
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + configuration.restCountriesApiKey())
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
        if (statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN) {
            return new RestCountriesQueryResult.SourceUnavailable(UNAUTHORIZED_REASON.formatted(statusCode));
        }
        if (statusCode < 200 || statusCode >= 300) {
            return new RestCountriesQueryResult.SourceUnavailable("HTTP " + statusCode);
        }
        try {
            JsonNode objects = objectMapper.readTree(body).path("data").path("objects");
            if (!objects.isArray()) {
                return new RestCountriesQueryResult.SourceUnavailable("expected data.objects array");
            }
            if (objects.isEmpty()) {
                return new RestCountriesQueryResult.NotFound();
            }
            List<CountryFacts> countries = new ArrayList<>();
            for (JsonNode node : objects) {
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

    private Optional<CountryFacts> mapCountry(JsonNode node) {
        Optional<String> countryName = textValue(node.path("names").path("common"));
        Optional<String> capital = selectCapital(node.path("capitals"));
        Optional<String> region = textValue(node.path("region"));
        if (countryName.isEmpty() || capital.isEmpty() || region.isEmpty() || !node.hasNonNull("population")) {
            return Optional.empty();
        }
        return Optional.of(new CountryFacts(
                countryName.get(), capital.get(), region.get(), node.path("population").asLong()));
    }

    private static Optional<String> selectCapital(JsonNode capitals) {
        if (!capitals.isArray() || capitals.isEmpty()) {
            return Optional.empty();
        }
        for (JsonNode capital : capitals) {
            if (capital.path("attributes").path("primary").asBoolean(false)) {
                return textValue(capital.path("name"));
            }
        }
        return textValue(capitals.get(0).path("name"));
    }

    private static Optional<String> textValue(JsonNode node) {
        if (node.isMissingNode() || node.isNull() || !node.isTextual()) {
            return Optional.empty();
        }
        final var text = StringUtils.trimToNull(node.asText());
        return Optional.ofNullable(text);
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
