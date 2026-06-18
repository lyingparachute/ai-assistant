import { describe, expect, it } from 'vitest';

import type { SourceContributionStatus, SourceType } from './types';
import { renderTraceStep, renderTraceTimeline } from './traceDisplay';

const XSS = `<script>alert("x's & y")</script>`;

function assertNoExecutableMarkup(html: string): void {
  expect(html).not.toContain('<script>');
  expect(html).not.toContain('</script>');
  expect(html).not.toContain(`alert("x's`);
  expect(html).toContain('&lt;script&gt;');
  expect(html).toContain('&quot;');
  expect(html).toContain('&#39;');
  expect(html).toContain('&amp;');
}

describe('renderTraceTimeline escaping', () => {
  it('escapes a malicious status value on a trace step', () => {
    const step = {
      type: 'countries_facts' as SourceType,
      status: XSS as SourceContributionStatus,
    };

    const html = renderTraceStep(step);
    assertNoExecutableMarkup(html);
    expect(html).toContain('class="trace-step-badge badge"');
  });

  it('escapes malicious status values across a timeline', () => {
    const steps = [
      { type: 'countries_facts' as SourceType, status: XSS as SourceContributionStatus },
      { type: 'weather_observation' as SourceType, status: XSS as SourceContributionStatus },
      { type: 'rag_knowledge' as SourceType, status: XSS as SourceContributionStatus },
      { type: 'model_synthesis' as SourceType, status: XSS as SourceContributionStatus },
    ];

    assertNoExecutableMarkup(renderTraceTimeline(steps));
  });
});

describe('renderTraceTimeline badges', () => {
  it('applies status-coloured badge classes', () => {
    const html = renderTraceTimeline([
      { type: 'countries_facts', status: 'USED' },
      { type: 'weather_observation', status: 'UNAVAILABLE' },
      { type: 'rag_knowledge', status: 'INSUFFICIENT' },
    ]);

    expect(html).toContain('badge-used');
    expect(html).toContain('badge-unavailable');
    expect(html).toContain('badge-insufficient');
  });
});

describe('renderTraceTimeline unknown types', () => {
  it('falls back to the escaped raw type when no label exists', () => {
    const html = renderTraceStep({
      type: 'future_source' as SourceType,
      status: 'USED',
    });

    expect(html).toContain('future_source');
    expect(html).not.toContain('undefined');
  });

  it('falls back to Unknown source when type is empty', () => {
    const html = renderTraceStep({
      type: '' as SourceType,
      status: 'USED',
    });

    expect(html).toContain('Unknown source');
  });
});

describe('renderTraceTimeline collapse', () => {
  it('collapses timelines longer than four steps', () => {
    const steps = Array.from({ length: 6 }, (_, index) => ({
      type: 'countries_facts' as SourceType,
      status: 'USED' as SourceContributionStatus,
    }));

    const html = renderTraceTimeline(steps);

    expect(html).toContain('2 more steps');
    expect(html).toContain('trace-timeline-nested');
    expect((html.match(/class="trace-step"/g) ?? []).length).toBe(6);
  });

  it('does not collapse timelines with four or fewer steps', () => {
    const html = renderTraceTimeline([
      { type: 'countries_facts', status: 'USED' },
      { type: 'weather_observation', status: 'USED' },
      { type: 'rag_knowledge', status: 'USED' },
      { type: 'model_synthesis', status: 'USED' },
    ]);

    expect(html).not.toContain('more step');
    expect(html).not.toContain('trace-timeline-nested');
  });
});
