import { describe, expect, it } from 'vitest';

import {
  appendTurn,
  createSession,
  getTurns,
  updateTurn,
} from './sessionDisplay';

describe('sessionDisplay', () => {
  it('creates a session, appends turns, and updates turn state', () => {
    const session = createSession();
    expect(getTurns(session)).toEqual([]);

    const first = appendTurn(session, 'What is the capital of Germany?');
    expect(first.status).toBe('pending');
    expect(first.question).toBe('What is the capital of Germany?');
    expect(getTurns(session)).toHaveLength(1);

    appendTurn(session, 'What is the temperature in Munich?');
    expect(getTurns(session)).toHaveLength(2);

    const updated = updateTurn(session, 0, {
      status: 'streaming',
      provisionalAnswerText: 'Berlin',
    });
    expect(updated?.status).toBe('streaming');
    expect(getTurns(session)[0]?.provisionalAnswerText).toBe('Berlin');
  });
});
