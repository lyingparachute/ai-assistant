package dev.localassistant.assistant.adapters.outbound.cdq;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.util.Objects;
import java.util.regex.Pattern;

public final class ProductPageTextExtractor {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public String extractPlainText(String html) {
        Objects.requireNonNull(html, "html");
        if (html.isBlank()) {
            throw new IllegalArgumentException("html must not be blank");
        }

        Document document = Jsoup.parse(html);
        document.select("script, style, noscript").remove();
        String bodyText = document.body() == null ? document.text() : document.body().text();
        String sanitized = Jsoup.clean(bodyText, Safelist.none());
        String normalized = WHITESPACE.matcher(sanitized.trim()).replaceAll(" ");
        if (normalized.isBlank()) {
            throw new ProductPageExtractionException("No extractable text found in product page HTML");
        }
        return normalized;
    }
}
