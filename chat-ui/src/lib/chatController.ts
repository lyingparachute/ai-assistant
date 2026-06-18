import type { TraceEventPayload, TokenEventPayload } from './api';
import {
  appendTurn,
  createSession,
  getTurns,
  updateTurn,
  type SessionDisplay,
  type SessionTurn,
  type TurnErrorKind,
  type TurnStatus,
} from './sessionDisplay';
import type { ApiErrorResponse, ChatResponse } from './types';

export type { SessionTurn, TurnErrorKind, TurnStatus };

/**
 * Turn lifecycle states managed by the Chat Interface controller.
 *
 * - `pending` — turn recorded; stream not yet active
 * - `streaming` — SSE stream open; trace and tokens may arrive
 * - `complete` — authoritative `final` received
 * - `aborted` — user stopped the stream; partial content retained
 * - `error` — HTTP, connection, or SSE `error` before a successful `final`
 */
export interface ChatControllerCallbacks {
  onTurnUpdated?: (turn: SessionTurn, index: number) => void;
  onValidationError?: (message: string) => void;
}

export interface ChatController {
  readonly session: SessionDisplay;
  readonly activeTurnIndex: number | null;
  submitQuestion(question: string): number;
  markStreaming(index: number): void;
  handleTrace(index: number, payload: TraceEventPayload): void;
  handleToken(index: number, payload: TokenEventPayload): void;
  handleFinal(index: number, response: ChatResponse): void;
  handleStreamError(index: number, payload: ApiErrorResponse): void;
  handleConnectionError(index: number, message: string): void;
  handleValidationError(message: string, turnIndex: number | null): void;
  stopActiveTurn(reason?: string): void;
  abortActiveStream(): void;
  getStreamAbortSignal(): AbortSignal | undefined;
  getTurnStatus(index: number): TurnStatus | undefined;
}

const TERMINAL_TURN_STATUSES: readonly TurnStatus[] = ['complete', 'aborted', 'error'];

export function createChatController(callbacks: ChatControllerCallbacks = {}): ChatController {
  const session = createSession();
  let activeTurnIndex: number | null = null;
  let activeAbortController: AbortController | null = null;

  const notify = (index: number): void => {
    const turn = getTurns(session)[index];
    if (turn) {
      callbacks.onTurnUpdated?.(turn, index);
    }
  };

  const shouldIgnoreStreamEvent = (index: number): boolean => {
    if (index !== activeTurnIndex) {
      return true;
    }
    const turn = getTurns(session)[index];
    if (!turn) {
      return true;
    }
    return TERMINAL_TURN_STATUSES.includes(turn.status);
  };

  const clearActiveStream = (): void => {
    activeAbortController = null;
    activeTurnIndex = null;
  };

  const abortActiveStream = (): void => {
    if (activeAbortController) {
      activeAbortController.abort();
      activeAbortController = null;
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
      if (activeTurnIndex !== null) {
        const priorIndex = activeTurnIndex;
        updateTurn(session, priorIndex, {
          status: 'aborted',
          abortReason: 'superseded-by-new-question',
          completedAt: Date.now(),
        });
        notify(priorIndex);
      }

      abortActiveStream();

      appendTurn(session, question);
      const index = session.turns.length - 1;
      activeTurnIndex = index;
      activeAbortController = new AbortController();
      notify(index);
      return index;
    },

    markStreaming(index: number): void {
      updateTurn(session, index, { status: 'streaming' });
      activeTurnIndex = index;
      notify(index);
    },

    handleTrace(index: number, payload: TraceEventPayload): void {
      if (shouldIgnoreStreamEvent(index)) {
        return;
      }
      const turn = getTurns(session)[index];
      if (!turn) {
        return;
      }
      updateTurn(session, index, {
        status: 'streaming',
        traceSteps: [...turn.traceSteps, payload],
      });
      notify(index);
    },

    handleToken(index: number, payload: TokenEventPayload): void {
      if (shouldIgnoreStreamEvent(index)) {
        return;
      }
      const turn = getTurns(session)[index];
      if (!turn) {
        return;
      }
      updateTurn(session, index, {
        status: 'streaming',
        provisionalAnswerText: turn.provisionalAnswerText + payload.text,
        sawTokens: true,
      });
      notify(index);
    },

    handleFinal(index: number, response: ChatResponse): void {
      if (shouldIgnoreStreamEvent(index)) {
        return;
      }
      updateTurn(session, index, {
        status: 'complete',
        finalResponse: response,
        provisionalAnswerText: response.answerText,
        completedAt: Date.now(),
      });
      notify(index);
      if (activeTurnIndex === index) {
        clearActiveStream();
      }
    },

    handleStreamError(index: number, payload: ApiErrorResponse): void {
      if (shouldIgnoreStreamEvent(index)) {
        return;
      }
      updateTurn(session, index, {
        status: 'error',
        errorKind: 'stream-error',
        errorMessage: payload.message,
        completedAt: Date.now(),
      });
      notify(index);
      if (activeTurnIndex === index) {
        clearActiveStream();
      }
    },

    handleConnectionError(index: number, message: string): void {
      if (shouldIgnoreStreamEvent(index)) {
        return;
      }
      updateTurn(session, index, {
        status: 'error',
        errorKind: 'connection-error',
        errorMessage: message,
        completedAt: Date.now(),
      });
      notify(index);
      if (activeTurnIndex === index) {
        clearActiveStream();
      }
    },

    handleValidationError(message: string, turnIndex: number | null): void {
      callbacks.onValidationError?.(message);
      if (turnIndex === null) {
        return;
      }
      updateTurn(session, turnIndex, {
        status: 'error',
        errorKind: 'validation-error',
        errorMessage: message,
        completedAt: Date.now(),
      });
      notify(turnIndex);
      if (activeTurnIndex === turnIndex) {
        clearActiveStream();
      }
    },

    stopActiveTurn(reason = 'user-abort'): void {
      if (activeTurnIndex === null) {
        return;
      }
      abortActiveStream();
      const index = activeTurnIndex;
      updateTurn(session, index, {
        status: 'aborted',
        abortReason: reason,
        completedAt: Date.now(),
      });
      notify(index);
      clearActiveStream();
    },

    abortActiveStream,

    getStreamAbortSignal(): AbortSignal | undefined {
      return activeAbortController?.signal;
    },

    getTurnStatus(index: number): TurnStatus | undefined {
      return getTurns(session)[index]?.status;
    },
  };
}
