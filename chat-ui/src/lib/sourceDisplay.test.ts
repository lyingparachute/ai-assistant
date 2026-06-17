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
