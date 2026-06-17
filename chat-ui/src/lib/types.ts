export type SourceContributionStatus = 'USED' | 'UNAVAILABLE' | 'INSUFFICIENT';

export type SourceType =
  | 'countries_facts'
  | 'weather_observation'
  | 'rag_knowledge'
  | 'model_synthesis';

export interface CountryInfo {
  countryName: string;
  capital: string;
  region: string;
  population: number;
}

export interface WeatherReport {
  location: { city: string };
  temperature: { celsius: number };
  timestamp: { kind: 'observed' | 'retrieved'; value: string };
}

export interface KnowledgeSnippet {
  chunkText: string;
  sourceUrl: string;
  contentHash: string;
  chunkIndex: number;
  retrievalSimilarityScore?: number;
}

interface SourceBase {
  type: SourceType;
  status: SourceContributionStatus;
}

export interface CountriesFactsSource extends SourceBase {
  type: 'countries_facts';
  countryInfo?: CountryInfo;
  unavailableMessage?: string;
  unavailableHint?: string;
}

export interface WeatherObservationSource extends SourceBase {
  type: 'weather_observation';
  weatherReport?: WeatherReport;
  unavailableMessage?: string;
  unavailableHint?: string;
}

export interface RagKnowledgeSource extends SourceBase {
  type: 'rag_knowledge';
  snippets?: KnowledgeSnippet[];
  unavailableMessage?: string;
  unavailableHint?: string;
}

export interface ModelSynthesisSource extends SourceBase {
  type: 'model_synthesis';
  unavailableMessage?: string;
  unavailableHint?: string;
}

export type SourceResponse =
  | CountriesFactsSource
  | WeatherObservationSource
  | RagKnowledgeSource
  | ModelSynthesisSource;

export interface ChatResponse {
  answerText: string;
  sources: SourceResponse[];
  traceCorrelationId?: string;
}

export interface ApiErrorResponse {
  error: string;
  message: string;
}
