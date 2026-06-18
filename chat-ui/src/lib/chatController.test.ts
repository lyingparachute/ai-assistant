import { describe, expect, it } from 'vitest';

import { createChatController } from './chatController';

describe('chatController', () => {
  it('tracks active turns through pending, streaming, and aborted states', () => {
    const updates: string[] = [];
    const controller = createChatController({
      onTurnUpdated: (turn) => {
        updates.push(turn.status);
      },
    });

    const index = controller.submitQuestion('What is CDQ Fraud Guard?');
    expect(index).toBe(0);
    expect(controller.getTurnStatus(index)).toBe('pending');
    expect(controller.activeTurnIndex).toBe(0);

    controller.markStreaming(index);
    expect(controller.getTurnStatus(index)).toBe('streaming');
    expect(updates).toEqual(['pending', 'streaming']);

    controller.stopActiveTurn();
    expect(controller.getTurnStatus(index)).toBe('aborted');
    expect(controller.activeTurnIndex).toBeNull();
    expect(controller.session.turns[0]?.abortReason).toBe('user-abort');
  });
});
