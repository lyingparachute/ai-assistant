package dev.localassistant.countries.schemas;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

public final class CountryToolSchemas {

    public static final String TOOL_NAME = "country_lookup";

    private CountryToolSchemas() {
    }

    public static McpSchema.JsonSchema inputSchema() {
        return new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "name",
                        Map.of(
                                "type", "string",
                                "description",
                                "English country name (for example Germany) or capital city name (for example Berlin)."
                        )
                ),
                List.of("name"),
                false,
                Map.of(),
                Map.of()
        );
    }
}
