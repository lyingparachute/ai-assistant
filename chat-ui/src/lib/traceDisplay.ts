import type { SourceContributionStatus, SourceType } from './types';

const SOURCE_LABELS: Record<SourceType, string> = {
  countries_facts: 'Country facts',
  weather_observation: 'Weather observation',
  rag_knowledge: 'RAG knowledge',
  model_synthesis: 'Model synthesis',
};

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

export interface TraceStep {
  type: SourceType;
  status: SourceContributionStatus;
}

export function renderTraceStep(step: TraceStep): string {
  return `<li class="trace-step">
    <span class="trace-step-label">${escapeHtml(SOURCE_LABELS[step.type])}</span>
    <span class="trace-step-badge badge">${escapeHtml(step.status)}</span>
  </li>`;
}

export function renderTraceTimeline(steps: TraceStep[]): string {
  if (steps.length === 0) {
    return '';
  }

  return `<ol class="trace-timeline" aria-label="Source-Usage Trace">${steps.map(renderTraceStep).join('')}</ol>`;
}
