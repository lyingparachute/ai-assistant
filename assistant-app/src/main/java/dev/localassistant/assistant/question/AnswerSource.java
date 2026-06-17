package dev.localassistant.assistant.question;

import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.WeatherReport;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface AnswerSource {

    record CountriesFacts(
            SourceContributionStatus status,
            CountryInfo resolvedCountryInfo,
            boolean hasResolvedCountryInfo,
            String unavailableMessage,
            String unavailableHint)
            implements AnswerSource {

        public CountriesFacts {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(unavailableMessage, "unavailableMessage");
            Objects.requireNonNull(unavailableHint, "unavailableHint");
            if (status == SourceContributionStatus.INSUFFICIENT) {
                throw new IllegalArgumentException("CountriesFacts does not support INSUFFICIENT status");
            }
            if (status == SourceContributionStatus.USED) {
                Objects.requireNonNull(resolvedCountryInfo, "resolvedCountryInfo");
                if (!hasResolvedCountryInfo) {
                    throw new IllegalArgumentException("USED CountriesFacts requires hasResolvedCountryInfo");
                }
                if (!unavailableMessage.isBlank() || !unavailableHint.isBlank()) {
                    throw new IllegalArgumentException("USED CountriesFacts must not carry unavailable details");
                }
            } else if (status == SourceContributionStatus.UNAVAILABLE) {
                if (hasResolvedCountryInfo || resolvedCountryInfo != null) {
                    throw new IllegalArgumentException("UNAVAILABLE CountriesFacts must not carry countryInfo");
                }
                if (unavailableMessage.isBlank() || unavailableHint.isBlank()) {
                    throw new IllegalArgumentException(
                            "UNAVAILABLE CountriesFacts requires unavailableMessage and unavailableHint");
                }
            }
        }

        public static CountriesFacts used(CountryInfo countryInfo) {
            return new CountriesFacts(
                    SourceContributionStatus.USED, countryInfo, true, "", "");
        }

        public static CountriesFacts unavailable(String message, String hint) {
            return new CountriesFacts(
                    SourceContributionStatus.UNAVAILABLE, null, false, message, hint);
        }

        public Optional<CountryInfo> countryInfo() {
            return hasResolvedCountryInfo ? Optional.of(resolvedCountryInfo) : Optional.empty();
        }
    }

    record WeatherObservation(
            SourceContributionStatus status,
            WeatherReport resolvedWeatherReport,
            boolean hasResolvedWeatherReport,
            String unavailableMessage,
            String unavailableHint)
            implements AnswerSource {

        public WeatherObservation {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(unavailableMessage, "unavailableMessage");
            Objects.requireNonNull(unavailableHint, "unavailableHint");
            if (status == SourceContributionStatus.INSUFFICIENT) {
                throw new IllegalArgumentException("WeatherObservation does not support INSUFFICIENT status");
            }
            if (status == SourceContributionStatus.USED) {
                Objects.requireNonNull(resolvedWeatherReport, "resolvedWeatherReport");
                if (!hasResolvedWeatherReport) {
                    throw new IllegalArgumentException("USED WeatherObservation requires hasResolvedWeatherReport");
                }
                if (!unavailableMessage.isBlank() || !unavailableHint.isBlank()) {
                    throw new IllegalArgumentException("USED WeatherObservation must not carry unavailable details");
                }
            } else if (status == SourceContributionStatus.UNAVAILABLE) {
                if (hasResolvedWeatherReport || resolvedWeatherReport != null) {
                    throw new IllegalArgumentException("UNAVAILABLE WeatherObservation must not carry weatherReport");
                }
                if (unavailableMessage.isBlank() || unavailableHint.isBlank()) {
                    throw new IllegalArgumentException(
                            "UNAVAILABLE WeatherObservation requires unavailableMessage and unavailableHint");
                }
            }
        }

        public static WeatherObservation used(WeatherReport weatherReport) {
            return new WeatherObservation(
                    SourceContributionStatus.USED, weatherReport, true, "", "");
        }

        public static WeatherObservation unavailable(String message, String hint) {
            return new WeatherObservation(
                    SourceContributionStatus.UNAVAILABLE, null, false, message, hint);
        }

        public Optional<WeatherReport> weatherReport() {
            return hasResolvedWeatherReport ? Optional.of(resolvedWeatherReport) : Optional.empty();
        }
    }

    record RagKnowledge(
            SourceContributionStatus status,
            List<KnowledgeSnippet> snippets,
            String unavailableMessage,
            String unavailableHint)
            implements AnswerSource {

        public RagKnowledge {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(snippets, "snippets");
            Objects.requireNonNull(unavailableMessage, "unavailableMessage");
            Objects.requireNonNull(unavailableHint, "unavailableHint");
            snippets = List.copyOf(snippets);
            if (status == SourceContributionStatus.USED) {
                if (snippets.isEmpty()) {
                    throw new IllegalArgumentException("USED RagKnowledge requires at least one snippet");
                }
                if (!unavailableMessage.isBlank() || !unavailableHint.isBlank()) {
                    throw new IllegalArgumentException("USED RagKnowledge must not carry unavailable details");
                }
            } else if (status == SourceContributionStatus.INSUFFICIENT) {
                if (!snippets.isEmpty()) {
                    throw new IllegalArgumentException("INSUFFICIENT RagKnowledge must not carry snippets");
                }
                if (!unavailableMessage.isBlank() || !unavailableHint.isBlank()) {
                    throw new IllegalArgumentException("INSUFFICIENT RagKnowledge must not carry unavailable details");
                }
            } else if (status == SourceContributionStatus.UNAVAILABLE) {
                if (!snippets.isEmpty()) {
                    throw new IllegalArgumentException("UNAVAILABLE RagKnowledge must not carry snippets");
                }
                if (unavailableMessage.isBlank() || unavailableHint.isBlank()) {
                    throw new IllegalArgumentException(
                            "UNAVAILABLE RagKnowledge requires unavailableMessage and unavailableHint");
                }
            }
        }

        public static RagKnowledge used(List<KnowledgeSnippet> snippets) {
            return new RagKnowledge(SourceContributionStatus.USED, snippets, "", "");
        }

        public static RagKnowledge insufficient() {
            return new RagKnowledge(SourceContributionStatus.INSUFFICIENT, List.of(), "", "");
        }

        public static RagKnowledge unavailable(String message, String hint) {
            return new RagKnowledge(SourceContributionStatus.UNAVAILABLE, List.of(), message, hint);
        }
    }

    record ModelSynthesis(
            SourceContributionStatus status, String unavailableMessage, String unavailableHint)
            implements AnswerSource {

        public ModelSynthesis {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(unavailableMessage, "unavailableMessage");
            Objects.requireNonNull(unavailableHint, "unavailableHint");
            if (status == SourceContributionStatus.INSUFFICIENT) {
                throw new IllegalArgumentException("ModelSynthesis does not support INSUFFICIENT status");
            }
            if (status == SourceContributionStatus.USED) {
                if (!unavailableMessage.isBlank() || !unavailableHint.isBlank()) {
                    throw new IllegalArgumentException("USED ModelSynthesis must not carry unavailable details");
                }
            } else if (status == SourceContributionStatus.UNAVAILABLE) {
                if (unavailableMessage.isBlank() || unavailableHint.isBlank()) {
                    throw new IllegalArgumentException(
                            "UNAVAILABLE ModelSynthesis requires unavailableMessage and unavailableHint");
                }
            }
        }

        public static ModelSynthesis used() {
            return new ModelSynthesis(SourceContributionStatus.USED, "", "");
        }

        public static ModelSynthesis unavailable(String message, String hint) {
            return new ModelSynthesis(SourceContributionStatus.UNAVAILABLE, message, hint);
        }
    }
}
