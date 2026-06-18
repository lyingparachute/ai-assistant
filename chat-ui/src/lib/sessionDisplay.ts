import type { ChatResponse } from './types';
import type { TraceStep } from './traceDisplay';

export type TurnStatus = 'pending' | 'streaming' | 'complete' | 'aborted' | 'error';

export type TurnErrorKind = 'validation-error' | 'connection-error' | 'stream-error';

export interface SessionTurn {
  question: string;
  status: TurnStatus;
  traceSteps: TraceStep[];
  provisionalAnswerText: string;
  finalResponse?: ChatResponse;
  startedAt: number;
  completedAt?: number;
  abortReason?: string;
  errorKind?: TurnErrorKind;
  errorMessage?: string;
  sawTokens?: boolean;
}

export interface SessionDisplay {
  turns: SessionTurn[];
}

export function createSession(): SessionDisplay {
  return { turns: [] };
}

export function appendTurn(session: SessionDisplay, question: string): SessionTurn {
  const turn: SessionTurn = {
    question,
    status: 'pending',
    traceSteps: [],
    provisionalAnswerText: '',
    startedAt: Date.now(),
  };
  session.turns.push(turn);
  return turn;
}

export function getTurns(session: SessionDisplay): readonly SessionTurn[] {
  return session.turns;
}

export function updateTurn(
  session: SessionDisplay,
  index: number,
  patch: Partial<Omit<SessionTurn, 'question' | 'startedAt'>>,
): SessionTurn | undefined {
  const turn = session.turns[index];
  if (!turn) {
    return undefined;
  }
  Object.assign(turn, patch);
  return turn;
}
