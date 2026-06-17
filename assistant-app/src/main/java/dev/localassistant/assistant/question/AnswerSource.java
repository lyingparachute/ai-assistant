package dev.localassistant.assistant.question;

import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.SourceUnavailability;
import dev.localassistant.assistant.tools.WeatherReport;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface AnswerSource {

    SourceContributionStatus status();

    sealed interface CountriesFacts extends AnswerSource {

        Optional<CountryInfo> countryInfo();

        static CountriesFacts used(CountryInfo countryInfo) {
            return new Used(countryInfo);
        }

        static CountriesFacts unavailable(SourceUnavailability unavailability) {
            return new Unavailable(unavailability);
        }

        record Used(CountryInfo resolvedCountryInfo) implements CountriesFacts {
            public Used {
                Objects.requireNonNull(resolvedCountryInfo, "resolvedCountryInfo");
            }

            @Override
            public SourceContributionStatus status() {
                return SourceContributionStatus.USED;
            }

            @Override
            public Optional<CountryInfo> countryInfo() {
                return Optional.of(resolvedCountryInfo);
            }
        }

        record Unavailable(SourceUnavailability unavailability) implements CountriesFacts {
            public Unavailable {
                Objects.requireNonNull(unavailability, "unavailability");
            }

            @Override
            public SourceContributionStatus status() {
                return SourceContributionStatus.UNAVAILABLE;
            }

            @Override
            public Optional<CountryInfo> countryInfo() {
                return Optional.empty();
            }
        }
    }

    sealed interface WeatherObservation extends AnswerSource {

        Optional<WeatherReport> weatherReport();

        static WeatherObservation used(WeatherReport weatherReport) {
            return new Used(weatherReport);
        }

        static WeatherObservation unavailable(SourceUnavailability unavailability) {
            return new Unavailable(unavailability);
        }

        record Used(WeatherReport resolvedWeatherReport) implements WeatherObservation {
            public Used {
                Objects.requireNonNull(resolvedWeatherReport, "resolvedWeatherReport");
            }

            @Override
            public SourceContributionStatus status() {
                return SourceContributionStatus.USED;
            }

            @Override
            public Optional<WeatherReport> weatherReport() {
                return Optional.of(resolvedWeatherReport);
            }
        }

        record Unavailable(SourceUnavailability unavailability) implements WeatherObservation {
            public Unavailable {
                Objects.requireNonNull(unavailability, "unavailability");
            }

            @Override
            public SourceContributionStatus status() {
                return SourceContributionStatus.UNAVAILABLE;
            }

            @Override
            public Optional<WeatherReport> weatherReport() {
                return Optional.empty();
            }
        }
    }

    sealed interface RagKnowledge extends AnswerSource {

        List<KnowledgeSnippet> snippets();

        static RagKnowledge used(List<KnowledgeSnippet> snippets) {
            return new Used(snippets);
        }

        static RagKnowledge insufficient() {
            return new Insufficient();
        }

        static RagKnowledge unavailable(SourceUnavailability unavailability) {
            return new Unavailable(unavailability);
        }

        record Used(List<KnowledgeSnippet> resolvedSnippets) implements RagKnowledge {
            public Used {
                Objects.requireNonNull(resolvedSnippets, "resolvedSnippets");
                resolvedSnippets = List.copyOf(resolvedSnippets);
                if (resolvedSnippets.isEmpty()) {
                    throw new IllegalArgumentException("USED RagKnowledge requires at least one snippet");
                }
            }

            @Override
            public SourceContributionStatus status() {
                return SourceContributionStatus.USED;
            }

            @Override
            public List<KnowledgeSnippet> snippets() {
                return resolvedSnippets;
            }
        }

        record Insufficient() implements RagKnowledge {

            @Override
            public SourceContributionStatus status() {
                return SourceContributionStatus.INSUFFICIENT;
            }

            @Override
            public List<KnowledgeSnippet> snippets() {
                return List.of();
            }
        }

        record Unavailable(SourceUnavailability unavailability) implements RagKnowledge {
            public Unavailable {
                Objects.requireNonNull(unavailability, "unavailability");
            }

            @Override
            public SourceContributionStatus status() {
                return SourceContributionStatus.UNAVAILABLE;
            }

            @Override
            public List<KnowledgeSnippet> snippets() {
                return List.of();
            }
        }
    }

    sealed interface ModelSynthesis extends AnswerSource {

        static ModelSynthesis used() {
            return new Used();
        }

        static ModelSynthesis unavailable(SourceUnavailability unavailability) {
            return new Unavailable(unavailability);
        }

        record Used() implements ModelSynthesis {

            @Override
            public SourceContributionStatus status() {
                return SourceContributionStatus.USED;
            }
        }

        record Unavailable(SourceUnavailability unavailability) implements ModelSynthesis {
            public Unavailable {
                Objects.requireNonNull(unavailability, "unavailability");
            }

            @Override
            public SourceContributionStatus status() {
                return SourceContributionStatus.UNAVAILABLE;
            }
        }
    }
}
