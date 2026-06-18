import { escapeHtml } from './htmlEscape';
import { resolveSourceLabel } from './sourceLabels';
import { statusBadgeClass } from './statusBadge';
import type { SourceContributionStatus, SourceType } from './types';

const TIMELINE_COLLAPSE_THRESHOLD = 4;

export interface TraceStep {
  type: SourceType;
  status: SourceContributionStatus;
}

export function renderTraceStep(step: TraceStep): string {
  return `<li class="trace-step">
    <span class="trace-step-label">${escapeHtml(resolveSourceLabel(step.type))}</span>
    <span class="trace-step-badge ${statusBadgeClass(step.status)}">${escapeHtml(step.status)}</span>
  </li>`;
}

function renderTraceSteps(steps: TraceStep[]): string {
  return steps.map(renderTraceStep).join('');
}

export function renderTraceTimeline(steps: TraceStep[]): string {
  if (steps.length === 0) {
    return '';
  }

  if (steps.length <= TIMELINE_COLLAPSE_THRESHOLD) {
    return `<ol class="trace-timeline" aria-label="Source-Usage Trace">${renderTraceSteps(steps)}</ol>`;
  }

  const visibleSteps = steps.slice(0, TIMELINE_COLLAPSE_THRESHOLD);
  const hiddenSteps = steps.slice(TIMELINE_COLLAPSE_THRESHOLD);
  const hiddenCount = hiddenSteps.length;
  const stepLabel = hiddenCount === 1 ? 'step' : 'steps';

  return `<ol class="trace-timeline" aria-label="Source-Usage Trace">${renderTraceSteps(visibleSteps)}<li class="trace-step-more"><details><summary>${hiddenCount} more ${stepLabel}</summary><ol class="trace-timeline-nested">${renderTraceSteps(hiddenSteps)}</ol></details></li></ol>`;
}
