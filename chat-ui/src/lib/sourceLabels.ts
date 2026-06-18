import type { SourceType } from './types';

export const SOURCE_LABELS: Record<SourceType, string> = {
  countries_facts: 'Country facts',
  weather_observation: 'Weather observation',
  rag_knowledge: 'RAG knowledge',
  model_synthesis: 'Model synthesis',
};

export function resolveSourceLabel(type: string): string {
  const known = SOURCE_LABELS[type as SourceType];
  if (known) {
    return known;
  }
  return type.length > 0 ? type : 'Unknown source';
}
