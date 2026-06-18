package dev.localassistant.assistant.rag.infrastructure;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.regex.Pattern;

@Component
@Profile("!test")
final class ProductPageTextExtractor {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public String extractPlainText(final String html) {
        Objects.requireNonNull(html, "html");
        if (StringUtils.isBlank(html)) {
            throw new IllegalArgumentException("html must not be blank");
        }

        final var document = Jsoup.parse(html);
        document.select("script, style, noscript").remove();
        final var bodyText = document.body() == null ? document.text() : document.body().text();
        final var sanitized = Jsoup.clean(bodyText, Safelist.none());
        final var normalized = WHITESPACE.matcher(StringUtils.trim(sanitized)).replaceAll(" ");
        if (StringUtils.isBlank(normalized)) {
            throw new ProductPageExtractionException("No extractable text found in product page HTML");
        }
        return normalized;
    }
}
