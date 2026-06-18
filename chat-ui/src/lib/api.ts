import { consumeSseEvents } from './sseParser';
import type { ApiErrorResponse, ChatResponse } from './types';
import type { SourceContributionStatus, SourceType } from './types';

const DEFAULT_API_URL = 'http://localhost:8080';

export function assistantApiUrl(): string {
  return import.meta.env.PUBLIC_ASSISTANT_API_URL ?? DEFAULT_API_URL;
}

export interface TraceEventPayload {
  type: SourceType;
  status: SourceContributionStatus;
}

export interface TokenEventPayload {
  text: string;
}

export interface StreamEventHandlers {
  onTrace: (payload: TraceEventPayload) => void;
  onToken: (payload: TokenEventPayload) => void;
  onFinal: (response: ChatResponse) => void;
  /** Invoked when the Assistant API emits an SSE `error` event after the stream opens. */
  onStreamError?: (payload: ApiErrorResponse) => void;
  /** Invoked when the client aborts the request via `AbortSignal`. */
  onAbort?: () => void;
}

export class StreamAbortedError extends Error {
  constructor() {
    super('Request aborted.');
    this.name = 'StreamAbortedError';
  }
}

export class HttpRequestError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'HttpRequestError';
    this.status = status;
  }
}

function isAbortError(error: unknown, signal?: AbortSignal): boolean {
  if (signal?.aborted) {
    return true;
  }
  return error instanceof DOMException && error.name === 'AbortError';
}

export async function streamQuestion(
  question: string,
  handlers: StreamEventHandlers,
  signal?: AbortSignal,
): Promise<ChatResponse> {
  let response: Response;
  try {
    response = await fetch(`${assistantApiUrl()}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question }),
      signal,
    });
  } catch (error) {
    if (isAbortError(error, signal)) {
      handlers.onAbort?.();
      throw new StreamAbortedError();
    }
    throw error;
  }

  if (!response.ok) {
    const errorBody = (await response.json().catch(() => null)) as ApiErrorResponse | null;
    const message = errorBody?.message ?? `Request failed with status ${response.status}`;
    throw new HttpRequestError(message, response.status);
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error('Assistant API returned an empty response body.');
  }

  const decoder = new TextDecoder();
  let buffer = '';
  let finalResponse: ChatResponse | null = null;
  let streamError: ApiErrorResponse | null = null;

  const dispatch = (name: string, data: string): void => {
    switch (name) {
      case 'trace':
        handlers.onTrace(JSON.parse(data) as TraceEventPayload);
        break;
      case 'token':
        handlers.onToken(JSON.parse(data) as TokenEventPayload);
        break;
      case 'final':
        finalResponse = JSON.parse(data) as ChatResponse;
        handlers.onFinal(finalResponse);
        break;
      case 'error':
        streamError = JSON.parse(data) as ApiErrorResponse;
        handlers.onStreamError?.(streamError);
        break;
    }
  };

  try {
    while (true) {
      if (signal?.aborted) {
        await reader.cancel();
        handlers.onAbort?.();
        throw new StreamAbortedError();
      }

      const { done, value } = await reader.read();
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const consumed = consumeSseEvents(buffer);
      buffer = consumed.remainder;

      for (const event of consumed.events) {
        dispatch(event.name, event.data);
      }
    }

    buffer += decoder.decode();
    const trailing = consumeSseEvents(buffer);
    for (const event of trailing.events) {
      dispatch(event.name, event.data);
    }
  } catch (error) {
    if (isAbortError(error, signal)) {
      handlers.onAbort?.();
      throw new StreamAbortedError();
    }
    throw error;
  }

  if (streamError) {
    throw new Error(streamError.message);
  }

  if (!finalResponse) {
    throw new Error('Assistant API stream did not include a final event.');
  }

  return finalResponse;
}
