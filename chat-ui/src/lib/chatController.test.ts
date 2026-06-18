import { describe, expect, it, vi } from 'vitest';

import { createChatController } from './chatController';
import type { ChatResponse } from './types';

const finalResponse: ChatResponse = {
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
  traceCorrelationId: 'trace-1',
};

describe('chatController', () => {
  it('appends a turn and moves through pending and streaming', () => {
    const updates: string[] = [];
    const controller = createChatController({
      onTurnUpdated: (turn) => updates.push(turn.status),
    });

    const index = controller.submitQuestion('What is the capital of Germany?');
    expect(index).toBe(0);
    expect(controller.session.turns).toHaveLength(1);
    expect(controller.getTurnStatus(index)).toBe('pending');
    expect(controller.activeTurnIndex).toBe(0);
    expect(controller.getStreamAbortSignal()).toBeDefined();

    controller.markStreaming(index);
    expect(controller.getTurnStatus(index)).toBe('streaming');
    expect(updates).toEqual(['pending', 'streaming']);
  });

  it('accumulates trace steps and tokens during streaming', () => {
    const controller = createChatController();

    const index = controller.submitQuestion('Weather in Munich?');
    controller.markStreaming(index);
    controller.handleTrace(index, { type: 'weather_observation', status: 'USED' });
    controller.handleToken(index, { text: 'It is ' });
    controller.handleToken(index, { text: '12°C.' });

    const turn = controller.session.turns[index];
    expect(turn?.traceSteps).toHaveLength(1);
    expect(turn?.provisionalAnswerText).toBe('It is 12°C.');
    expect(turn?.sawTokens).toBe(true);
    expect(turn?.status).toBe('streaming');
  });

  it('marks a turn complete on final and clears the active stream', () => {
    const controller = createChatController();

    const index = controller.submitQuestion('Capital of Germany?');
    controller.markStreaming(index);
    controller.handleFinal(index, finalResponse);

    const turn = controller.session.turns[index];
    expect(turn?.status).toBe('complete');
    expect(turn?.finalResponse).toEqual(finalResponse);
    expect(turn?.provisionalAnswerText).toBe(finalResponse.answerText);
    expect(turn?.completedAt).toBeTypeOf('number');
    expect(controller.activeTurnIndex).toBeNull();
    expect(controller.getStreamAbortSignal()).toBeUndefined();
  });

  it('stops the active turn and retains partial content', () => {
    const controller = createChatController();

    const index = controller.submitQuestion('Long answer please');
    controller.markStreaming(index);
    controller.handleTrace(index, { type: 'model_synthesis', status: 'USED' });
    controller.handleToken(index, { text: 'Partial ' });
    controller.stopActiveTurn();

    const turn = controller.session.turns[index];
    expect(turn?.status).toBe('aborted');
    expect(turn?.abortReason).toBe('user-abort');
    expect(turn?.provisionalAnswerText).toBe('Partial ');
    expect(turn?.traceSteps).toHaveLength(1);
    expect(controller.activeTurnIndex).toBeNull();
  });

  it('records stream errors while keeping partial trace and tokens', () => {
    const controller = createChatController();

    const index = controller.submitQuestion('Fail mid-stream');
    controller.markStreaming(index);
    controller.handleTrace(index, { type: 'rag_knowledge', status: 'INSUFFICIENT' });
    controller.handleToken(index, { text: 'Draft' });
    controller.handleStreamError(index, { error: 'source_unavailable', message: 'RAG failed.' });

    const turn = controller.session.turns[index];
    expect(turn?.status).toBe('error');
    expect(turn?.errorKind).toBe('stream-error');
    expect(turn?.errorMessage).toBe('RAG failed.');
    expect(turn?.traceSteps).toHaveLength(1);
    expect(turn?.provisionalAnswerText).toBe('Draft');
    expect(controller.activeTurnIndex).toBeNull();
  });

  it('records connection and validation errors', () => {
    const validationMessages: string[] = [];
    const controller = createChatController({
      onValidationError: (message) => validationMessages.push(message),
    });

    const connectionIndex = controller.submitQuestion('Offline?');
    controller.markStreaming(connectionIndex);
    controller.handleConnectionError(connectionIndex, 'Network down.');

    expect(controller.session.turns[connectionIndex]?.errorKind).toBe('connection-error');
    expect(controller.session.turns[connectionIndex]?.status).toBe('error');

    const validationIndex = controller.submitQuestion('Bad input');
    controller.handleValidationError('Question is too long.', validationIndex);

    expect(validationMessages).toEqual(['Question is too long.']);
    expect(controller.session.turns[validationIndex]?.errorKind).toBe('validation-error');
    expect(controller.session.turns[validationIndex]?.status).toBe('error');
  });

  it('aborts an in-flight stream when submitting a new question', () => {
    const controller = createChatController();

    const firstIndex = controller.submitQuestion('First question');
    const firstSignal = controller.getStreamAbortSignal();
    controller.markStreaming(firstIndex);
    controller.handleToken(firstIndex, { text: 'First ' });

    const secondIndex = controller.submitQuestion('Second question');
    expect(firstSignal?.aborted).toBe(true);
    expect(controller.session.turns[firstIndex]?.status).toBe('aborted');
    expect(controller.session.turns[firstIndex]?.abortReason).toBe('superseded-by-new-question');
    expect(secondIndex).toBe(1);
    expect(controller.activeTurnIndex).toBe(1);
    expect(controller.getStreamAbortSignal()).not.toBe(firstSignal);
  });

  it('exposes abortActiveStream for explicit cancellation', () => {
    const controller = createChatController();
    controller.submitQuestion('Cancel me');
    const signal = controller.getStreamAbortSignal();

    controller.abortActiveStream();

    expect(signal?.aborted).toBe(true);
    expect(controller.getStreamAbortSignal()).toBeUndefined();
  });

  it('ignores late SSE events after user abort', () => {
    const controller = createChatController();

    const index = controller.submitQuestion('Abort then linger');
    controller.markStreaming(index);
    controller.handleTrace(index, { type: 'countries_facts', status: 'USED' });
    controller.handleToken(index, { text: 'Partial ' });
    controller.stopActiveTurn();

    controller.handleTrace(index, { type: 'weather_observation', status: 'USED' });
    controller.handleToken(index, { text: 'late token' });
    controller.handleFinal(index, finalResponse);
    controller.handleStreamError(index, { error: 'source_unavailable', message: 'Too late.' });
    controller.handleConnectionError(index, 'Too late.');

    const turn = controller.session.turns[index];
    expect(turn?.status).toBe('aborted');
    expect(turn?.traceSteps).toHaveLength(1);
    expect(turn?.provisionalAnswerText).toBe('Partial ');
  });

  it('ignores late SSE events after supersede by a new question', () => {
    const controller = createChatController();

    const firstIndex = controller.submitQuestion('First');
    controller.markStreaming(firstIndex);
    controller.handleToken(firstIndex, { text: 'First ' });

    const secondIndex = controller.submitQuestion('Second');
    controller.markStreaming(secondIndex);

    controller.handleToken(firstIndex, { text: 'stale token' });
    controller.handleFinal(firstIndex, finalResponse);
    controller.handleStreamError(firstIndex, {
      error: 'source_unavailable',
      message: 'Stale error.',
    });

    const firstTurn = controller.session.turns[firstIndex];
    expect(firstTurn?.status).toBe('aborted');
    expect(firstTurn?.provisionalAnswerText).toBe('First ');
    expect(controller.activeTurnIndex).toBe(secondIndex);
  });
});
