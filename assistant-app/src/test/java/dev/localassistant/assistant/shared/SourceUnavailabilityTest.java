package dev.localassistant.assistant.shared;

import dev.localassistant.assistant.countryfacts.domain.CountryInfo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceUnavailabilityTest {

    @Test
    void rejectsBlankSourceLabel() {
        assertThatThrownBy(() -> new SourceUnavailability(" ", "message", "hint"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceLabel");
    }

    @Test
    void rejectsBlankMessage() {
        assertThatThrownBy(() -> new SourceUnavailability("countries MCP", " ", "hint"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }

    @Test
    void rejectsBlankHint() {
        assertThatThrownBy(() -> new SourceUnavailability("countries MCP", "message", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hint");
    }

    @Test
    void toolErrorCoalescesToFallbackLabel() {
        ToolExecutionResult<CountryInfo> result =
                new ToolExecutionResult.ToolError<>("lookup failed", "retry");

        SourceUnavailability unavailability = result.asUnavailability("Countries MCP");

        assertThat(unavailability.sourceLabel()).isEqualTo("Countries MCP");
        assertThat(unavailability.message()).isEqualTo("lookup failed");
        assertThat(unavailability.hint()).isEqualTo("retry");
    }

    @Test
    void sourceUnavailableKeepsItsOwnLabel() {
        ToolExecutionResult<CountryInfo> result =
                new ToolExecutionResult.SourceUnavailable<>("countries MCP", "down", "retry");

        SourceUnavailability unavailability = result.asUnavailability("Countries MCP");

        assertThat(unavailability.sourceLabel()).isEqualTo("countries MCP");
        assertThat(unavailability.message()).isEqualTo("down");
    }

    @Test
    void successHasNoUnavailability() {
        ToolExecutionResult<CountryInfo> result =
                new ToolExecutionResult.Success<>(new CountryInfo("Germany", "Berlin", "Europe", 1L));

        assertThatThrownBy(() -> result.asUnavailability("Countries MCP"))
                .isInstanceOf(IllegalStateException.class);
    }
}
