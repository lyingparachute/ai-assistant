/**
 * @vitest-environment happy-dom
 */
import { describe, expect, it } from 'vitest';

import { createChatController } from './chatController';
import { createDomAdapter, createDomScaffold } from './domAdapter';
import type { ChatResponse } from './types';

function mountScaffold() {
  const doc = document;
  const elements = createDomScaffold(doc);
  const workspace = doc.createElement('main');
  workspace.appendChild(elements.messageThread);
  workspace.appendChild(elements.status);
  workspace.appendChild(elements.form);
  doc.body.appendChild(workspace);
  return elements;
}

describe('domAdapter', () => {
  it('renders session thread with a11y scaffold attributes', () => {
    const elements = mountScaffold();
    const dom = createDomAdapter(elements);
    const controller = createChatController({
      onTurnUpdated: () => {
        dom.renderSession(controller.session, controller.activeTurnIndex);
      },
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

    expect(elements.messageThread.getAttribute('role')).toBe('log');
    expect(elements.messageThread.getAttribute('aria-label')).toBe(
      'Chat Interface session display',
    );

    const turns = elements.messageThread.querySelectorAll('.turn');
    expect(turns).toHaveLength(1);

    const liveAnswer = elements.messageThread.querySelector('.answer-text[aria-live="polite"]');
    expect(liveAnswer).toBeNull();

    const answerText = elements.messageThread.querySelector('.answer-text');
    expect(answerText?.textContent).toBe(response.answerText);
    expect(elements.messageThread.querySelector('.trace-timeline')).not.toBeNull();
    expect(elements.messageThread.querySelector('.source-card')).not.toBeNull();
    expect(elements.messageThread.querySelector('.trace-id')?.textContent).toBe(
      'Trace: trace-abc',
    );
  });

  it('renders at least two turns in the session display thread', () => {
    const elements = mountScaffold();
    const dom = createDomAdapter(elements);
    const controller = createChatController({
      onTurnUpdated: () => {
        dom.renderSession(controller.session, controller.activeTurnIndex);
      },
    });

    const firstResponse: ChatResponse = {
      answerText: 'Berlin.',
      sources: [{ type: 'countries_facts', status: 'USED' }],
    };
    const secondResponse: ChatResponse = {
      answerText: 'Munich weather is mild.',
      sources: [{ type: 'weather_observation', status: 'USED' }],
    };

    const firstIndex = controller.submitQuestion('Capital of Germany?');
    controller.markStreaming(firstIndex);
    controller.handleFinal(firstIndex, firstResponse);

    const secondIndex = controller.submitQuestion('Weather in Munich?');
    controller.markStreaming(secondIndex);
    controller.handleFinal(secondIndex, secondResponse);

    const turns = elements.messageThread.querySelectorAll('.turn');
    expect(turns).toHaveLength(2);
    expect(turns[0]?.querySelector('.user-question')?.textContent).toBe(
      'Capital of Germany?',
    );
    expect(turns[1]?.querySelector('.user-question')?.textContent).toBe('Weather in Munich?');
    expect(turns[1]?.querySelector('.answer-text[aria-live="polite"]')).toBeNull();
  });

  it('shows aborted and stream-error labels with partial answer text', () => {
    const elements = mountScaffold();
    const dom = createDomAdapter(elements);
    const controller = createChatController({
      onTurnUpdated: () => {
        dom.renderSession(controller.session, controller.activeTurnIndex);
      },
    });

    const index = controller.submitQuestion('Stop me');
    controller.markStreaming(index);
    controller.handleToken(index, { text: 'Partial answer' });
    controller.stopActiveTurn();

    const formingHint = elements.messageThread.querySelector('.forming-hint');
    expect(formingHint?.textContent).toBe('Stopped — incomplete');
    expect(elements.messageThread.querySelector('.answer-text')?.textContent).toBe(
      'Partial answer',
    );

    const errorIndex = controller.submitQuestion('Stream fail');
    controller.markStreaming(errorIndex);
    controller.handleTrace(errorIndex, { type: 'rag_knowledge', status: 'INSUFFICIENT' });
    controller.handleToken(errorIndex, { text: 'Partial stream answer' });
    controller.handleStreamError(errorIndex, {
      error: 'source_unavailable',
      message: 'Assistant stream failed.',
    });

    const turns = elements.messageThread.querySelectorAll('.turn');
    expect(turns).toHaveLength(2);
    const activeTurn = turns[1];
    expect(activeTurn?.querySelector('.forming-hint')?.textContent).toBe(
      'Assistant stream failed.',
    );
    expect(activeTurn?.querySelector('.answer-text')?.textContent).toBe('Partial stream answer');
    expect(activeTurn?.querySelector('.trace-timeline')).not.toBeNull();
  });

  it('sets aria-live on the active turn answer while streaming', () => {
    const elements = mountScaffold();
    const dom = createDomAdapter(elements);
    const controller = createChatController({
      onTurnUpdated: () => {
        dom.renderSession(controller.session, controller.activeTurnIndex);
      },
    });

    const index = controller.submitQuestion('Streaming now');
    controller.markStreaming(index);
    dom.renderSession(controller.session, controller.activeTurnIndex);

    const liveAnswer = elements.messageThread.querySelector('.answer-text[aria-live="polite"]');
    expect(liveAnswer).not.toBeNull();
  });

  it('shows final answer without tokens and hides forming hint', () => {
    const elements = mountScaffold();
    const dom = createDomAdapter(elements);
    const controller = createChatController({
      onTurnUpdated: () => {
        dom.renderSession(controller.session, controller.activeTurnIndex);
      },
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
    };

    const index = controller.submitQuestion('Capital of Germany?');
    controller.markStreaming(index);
    controller.handleTrace(index, { type: 'countries_facts', status: 'USED' });
    dom.renderSession(controller.session, controller.activeTurnIndex);

    const formingHintWhileStreaming = elements.messageThread.querySelector('.forming-hint');
    expect(formingHintWhileStreaming?.hidden).toBe(true);
    expect(elements.messageThread.querySelector('.answer-text')?.textContent).toBe('');

    controller.handleFinal(index, response);
    dom.renderSession(controller.session, controller.activeTurnIndex);

    const formingHintAfterFinal = elements.messageThread.querySelector('.forming-hint');
    expect(formingHintAfterFinal?.hidden).toBe(true);
    expect(elements.messageThread.querySelector('.answer-text')?.textContent).toBe(
      response.answerText,
    );
  });

  it('renders validation-error label in the thread', () => {
    const elements = mountScaffold();
    const dom = createDomAdapter(elements);
    const controller = createChatController({
      onTurnUpdated: () => {
        dom.renderSession(controller.session, controller.activeTurnIndex);
      },
      onValidationError: (message) => {
        dom.showValidationStatus(message);
      },
    });

    const index = controller.submitQuestion('x'.repeat(5000));
    controller.markStreaming(index);
    controller.handleValidationError('Question is too long.', index);

    expect(elements.messageThread.querySelectorAll('.turn')).toHaveLength(1);
    expect(elements.messageThread.querySelector('.forming-hint')?.textContent).toBe(
      'Question is too long.',
    );
  });
});
