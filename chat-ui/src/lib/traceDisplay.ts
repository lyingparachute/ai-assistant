import { escapeHtml } from './htmlEscape';
import { SOURCE_LABELS } from './sourceLabels';
import type { SourceContributionStatus, SourceType } from './types';

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
