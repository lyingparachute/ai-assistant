import { describe, expect, it } from 'vitest';

import { renderSources } from './sourceDisplay';
import type { SourceResponse } from './types';

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

describe('renderSources unknown types', () => {
  it('falls back to the escaped raw type when no label exists', () => {
    const html = renderSources([
      {
        type: 'future_source' as SourceResponse['type'],
        status: 'UNAVAILABLE',
        unavailableMessage: 'Not wired yet.',
      } as SourceResponse,
    ]);

    expect(html).toContain('future_source');
    expect(html).toContain('Not wired yet.');
    expect(html).not.toContain('undefined');
  });

  it('falls back to Unknown source when type is empty', () => {
    const html = renderSources([
      {
        type: '' as SourceResponse['type'],
        status: 'UNAVAILABLE',
      } as SourceResponse,
    ]);

    expect(html).toContain('Unknown source');
  });
});

describe('renderSources escaping', () => {
  it('escapes a used countries_facts payload', () => {
    const sources: SourceResponse[] = [
      {
        type: 'countries_facts',
        status: 'USED',
        countryInfo: { countryName: XSS, capital: XSS, region: XSS, population: 42 },
      },
    ];

    assertNoExecutableMarkup(renderSources(sources));
  });

  it('escapes a used weather_observation payload', () => {
    const sources: SourceResponse[] = [
      {
        type: 'weather_observation',
        status: 'USED',
        weatherReport: {
          location: { city: XSS },
          temperature: { celsius: 12 },
          timestamp: { kind: 'observed', value: XSS },
        },
      },
    ];

    assertNoExecutableMarkup(renderSources(sources));
  });

  it('escapes a used rag_knowledge payload', () => {
    const sources: SourceResponse[] = [
      {
        type: 'rag_knowledge',
        status: 'USED',
        snippets: [
          {
            chunkText: XSS,
            sourceUrl: XSS,
            contentHash: 'hash',
            chunkIndex: 0,
          },
        ],
      },
    ];

    assertNoExecutableMarkup(renderSources(sources));
  });

  it('escapes the unavailable path across every source type', () => {
    const types: SourceResponse['type'][] = [
      'countries_facts',
      'weather_observation',
      'rag_knowledge',
      'model_synthesis',
    ];

    for (const type of types) {
      const source = {
        type,
        status: 'UNAVAILABLE',
        unavailableMessage: XSS,
        unavailableHint: XSS,
      } as SourceResponse;

      assertNoExecutableMarkup(renderSources([source]));
    }
  });
});
