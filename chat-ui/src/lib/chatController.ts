import {
  appendTurn,
  createSession,
  getTurns,
  updateTurn,
  type SessionDisplay,
  type SessionTurn,
  type TurnStatus,
} from './sessionDisplay';

export type { SessionTurn, TurnStatus };

/**
 * Turn lifecycle states managed by the Chat Interface controller.
 *
 * - `pending` — turn recorded; stream not yet active
 * - `streaming` — SSE stream open; trace and tokens may arrive
 * - `complete` — authoritative `final` received
 * - `aborted` — user stopped the stream; partial content retained
 * - `error` — HTTP, connection, or SSE `error` before a successful `final`
 */
export type ControllerTurnState = TurnStatus;

export interface ChatControllerCallbacks {
  onTurnUpdated?: (turn: SessionTurn, index: number) => void;
}

export interface ChatController {
  readonly session: SessionDisplay;
  readonly activeTurnIndex: number | null;
  submitQuestion(question: string): number;
  markStreaming(index: number): void;
  stopActiveTurn(reason?: string): void;
  getTurnStatus(index: number): TurnStatus | undefined;
}

export function createChatController(callbacks: ChatControllerCallbacks = {}): ChatController {
  const session = createSession();
  let activeTurnIndex: number | null = null;

  const notify = (index: number): void => {
    const turn = getTurns(session)[index];
    if (turn) {
      callbacks.onTurnUpdated?.(turn, index);
    }
  };

  return {
    get session() {
      return session;
    },

    get activeTurnIndex() {
      return activeTurnIndex;
    },

    submitQuestion(question: string): number {
      appendTurn(session, question);
      const index = session.turns.length - 1;
      activeTurnIndex = index;
      notify(index);
      return index;
    },

    markStreaming(index: number): void {
      updateTurn(session, index, { status: 'streaming' });
      activeTurnIndex = index;
      notify(index);
    },

    stopActiveTurn(reason = 'user-abort'): void {
      if (activeTurnIndex === null) {
        return;
      }
      updateTurn(session, activeTurnIndex, {
        status: 'aborted',
        abortReason: reason,
        completedAt: Date.now(),
      });
      notify(activeTurnIndex);
      activeTurnIndex = null;
    },

    getTurnStatus(index: number): TurnStatus | undefined {
      return getTurns(session)[index]?.status;
    },
  };
}
