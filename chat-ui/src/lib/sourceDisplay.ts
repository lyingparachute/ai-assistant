import type { SourceResponse } from './types';

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function unavailableBlock(message: string, hint?: string): string {
  const hintHtml = hint ? `<p class="hint">${escapeHtml(hint)}</p>` : '';
  return `<p class="unavailable">${escapeHtml(message)}</p>${hintHtml}`;
}

function renderCountriesFacts(source: Extract<SourceResponse, { type: 'countries_facts' }>): string {
  if (source.status === 'USED' && source.countryInfo) {
    const info = source.countryInfo;
    return `<ul>
      <li><strong>Country:</strong> ${escapeHtml(info.countryName)}</li>
      <li><strong>Capital:</strong> ${escapeHtml(info.capital)}</li>
      <li><strong>Region:</strong> ${escapeHtml(info.region)}</li>
      <li><strong>Population:</strong> ${info.population.toLocaleString()}</li>
    </ul>`;
  }
  return unavailableBlock(source.unavailableMessage ?? 'Country facts unavailable', source.unavailableHint);
}

function renderWeatherObservation(
  source: Extract<SourceResponse, { type: 'weather_observation' }>,
): string {
  if (source.status === 'USED' && source.weatherReport) {
    const report = source.weatherReport;
    const label = report.timestamp.kind === 'observed' ? 'Observed' : 'Retrieved';
    return `<ul>
      <li><strong>Location:</strong> ${escapeHtml(report.location.city)}</li>
      <li><strong>Temperature:</strong> ${report.temperature.celsius}°C</li>
      <li><strong>${label}:</strong> ${escapeHtml(report.timestamp.value)}</li>
    </ul>`;
  }
  return unavailableBlock(source.unavailableMessage ?? 'Weather observation unavailable', source.unavailableHint);
}

function renderRagKnowledge(source: Extract<SourceResponse, { type: 'rag_knowledge' }>): string {
  if (source.status === 'INSUFFICIENT') {
    return '<p class="status">Insufficient product knowledge retrieved.</p>';
  }
  if (source.status === 'USED' && source.snippets && source.snippets.length > 0) {
    const items = source.snippets
      .map(
        (snippet) =>
          `<li><p>${escapeHtml(snippet.chunkText)}</p><p class="meta">Source: ${escapeHtml(snippet.sourceUrl)}</p></li>`,
      )
      .join('');
    return `<ul class="snippets">${items}</ul>`;
  }
  return unavailableBlock(source.unavailableMessage ?? 'RAG knowledge unavailable', source.unavailableHint);
}

function renderModelSynthesis(source: Extract<SourceResponse, { type: 'model_synthesis' }>): string {
  if (source.status === 'USED') {
    return '<p>Model synthesis contributed to the answer text.</p>';
  }
  return unavailableBlock(source.unavailableMessage ?? 'Model synthesis unavailable', source.unavailableHint);
}

const SOURCE_LABELS: Record<SourceResponse['type'], string> = {
  countries_facts: 'Country facts',
  weather_observation: 'Weather observation',
  rag_knowledge: 'RAG knowledge',
  model_synthesis: 'Model synthesis',
};

export function renderSources(sources: SourceResponse[]): string {
  if (sources.length === 0) {
    return '<p class="empty">No sources reported.</p>';
  }

  return sources
    .map((source) => {
      const body = (() => {
        switch (source.type) {
          case 'countries_facts':
            return renderCountriesFacts(source);
          case 'weather_observation':
            return renderWeatherObservation(source);
          case 'rag_knowledge':
            return renderRagKnowledge(source);
          case 'model_synthesis':
            return renderModelSynthesis(source);
        }
      })();

      return `<article class="source-card">
        <header>
          <h3>${escapeHtml(SOURCE_LABELS[source.type])}</h3>
          <span class="badge">${escapeHtml(source.status)}</span>
        </header>
        ${body}
      </article>`;
    })
    .join('');
}
