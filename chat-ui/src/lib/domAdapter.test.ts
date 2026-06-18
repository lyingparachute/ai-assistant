/**
 * @vitest-environment happy-dom
 */
import { describe, expect, it } from 'vitest';

import { createChatController } from './chatController';
import { createDomAdapter, createDomScaffold } from './domAdapter';
import type { ChatResponse } from './types';

describe('domAdapter', () => {
  it('renders a completed turn through the controller scaffold', () => {
    const elements = createDomScaffold();
    const dom = createDomAdapter(elements);
    const controller = createChatController({
      onTurnUpdated: (turn) => dom.renderTurn(turn),
    });

    const response: ChatResponse = {
      answerText: 'Berlin is the capital of Germany.',
      sources: [
        {
          type: 'countries_facts',
          status: 'USED',
          countryInfo: {
            countryName: 'Germany',
            capital: 'Berlin',
            region: 'Europe',
            population: 83_000_000,
          },
        },
      ],
      traceCorrelationId: 'trace-abc',
    };

    const index = controller.submitQuestion('Capital of Germany?');
    controller.markStreaming(index);
    controller.handleTrace(index, { type: 'countries_facts', status: 'USED' });
    controller.handleToken(index, { text: 'Berlin ' });
    controller.handleFinal(index, response);

    expect(elements.answerPanel.hidden).toBe(false);
    expect(elements.answerText.textContent).toBe(response.answerText);
    expect(elements.traceTimeline.querySelector('.trace-timeline')).not.toBeNull();
    expect(elements.sources.querySelector('.source-card')).not.toBeNull();
    expect(elements.traceId.textContent).toBe('Trace: trace-abc');
  });

  it('shows aborted and stream-error labels with partial answer text', () => {
    const elements = createDomScaffold();
    const dom = createDomAdapter(elements);
    const controller = createChatController({
      onTurnUpdated: (turn) => dom.renderTurn(turn),
    });

    const index = controller.submitQuestion('Stop me');
    controller.markStreaming(index);
    controller.handleToken(index, { text: 'Partial answer' });
    controller.stopActiveTurn();

    expect(elements.formingHint.textContent).toBe('Stopped — incomplete');
    expect(elements.answerText.textContent).toBe('Partial answer');

    const errorIndex = controller.submitQuestion('Stream fail');
    controller.markStreaming(errorIndex);
    controller.handleTrace(errorIndex, { type: 'rag_knowledge', status: 'INSUFFICIENT' });
    controller.handleToken(errorIndex, { text: 'Partial stream answer' });
    controller.handleStreamError(errorIndex, {
      error: 'source_unavailable',
      message: 'Assistant stream failed.',
    });

    expect(elements.formingHint.textContent).toBe('Assistant stream failed.');
    expect(elements.answerText.textContent).toBe('Partial stream answer');
    expect(elements.traceTimeline.querySelector('.trace-timeline')).not.toBeNull();
  });
});
