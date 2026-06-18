import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  HttpRequestError,
  StreamAbortedError,
  streamQuestion,
  type StreamEventHandlers,
} from './api';
import type { ChatResponse } from './types';

const finalPayload: ChatResponse = {
  answerText: 'Done.',
  sources: [],
};

function sseBody(events: string): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  const payload = events.endsWith('\n\n') ? events : `${events}\n\n`;
  return new ReadableStream({
    start(controller) {
      controller.enqueue(encoder.encode(payload));
      controller.close();
    },
  });
}

function handlers(overrides: Partial<StreamEventHandlers> = {}): StreamEventHandlers & {
  trace: ReturnType<typeof vi.fn>;
  token: ReturnType<typeof vi.fn>;
  final: ReturnType<typeof vi.fn>;
  streamError: ReturnType<typeof vi.fn>;
  abort: ReturnType<typeof vi.fn>;
} {
  const trace = vi.fn();
  const token = vi.fn();
  const final = vi.fn();
  const streamError = vi.fn();
  const abort = vi.fn();

  return {
    trace,
    token,
    final,
    streamError,
    abort,
    onTrace: overrides.onTrace ?? trace,
    onToken: overrides.onToken ?? token,
    onFinal: overrides.onFinal ?? final,
    onStreamError: overrides.onStreamError ?? streamError,
    onAbort: overrides.onAbort ?? abort,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('streamQuestion', () => {
  it('passes AbortSignal to fetch and invokes onAbort when aborted', async () => {
    const abortController = new AbortController();
    const fetchMock = vi.fn().mockImplementation((_url: string, init?: RequestInit) => {
      return new Promise<Response>((_resolve, reject) => {
        if (init?.signal?.aborted) {
          reject(new DOMException('Aborted', 'AbortError'));
          return;
        }
        init?.signal?.addEventListener('abort', () => {
          reject(new DOMException('Aborted', 'AbortError'));
        });
        queueMicrotask(() => abortController.abort());
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    const h = handlers();

    await expect(streamQuestion('Hello?', h, abortController.signal)).rejects.toBeInstanceOf(
      StreamAbortedError,
    );
    expect(h.abort).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/chat'),
      expect.objectContaining({ signal: abortController.signal }),
    );
  });

  it('throws HttpRequestError for HTTP 400 responses', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: async () => ({ error: 'validation_failed', message: 'Question is required.' }),
      }),
    );

    const h = handlers();
    await expect(streamQuestion(' ', h)).rejects.toBeInstanceOf(HttpRequestError);
    await expect(streamQuestion(' ', h)).rejects.toThrow('Question is required.');
    expect(h.streamError).not.toHaveBeenCalled();
  });

  it('dispatches onStreamError for SSE error events and still throws', async () => {
    const stream = sseBody(
      [
        'event: trace',
        'data: {"type":"countries_facts","status":"USED"}',
        '',
        'event: error',
        'data: {"error":"source_unavailable","message":"Countries source failed."}',
        '',
      ].join('\n'),
    );

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        body: stream,
      }),
    );

    const h = handlers();
    await expect(streamQuestion('Germany?', h)).rejects.toThrow('Countries source failed.');
    expect(h.streamError).toHaveBeenCalledWith({
      error: 'source_unavailable',
      message: 'Countries source failed.',
    });
    expect(h.trace).toHaveBeenCalledOnce();
    expect(h.final).not.toHaveBeenCalled();
  });

  it('returns the final response when the stream completes successfully', async () => {
    const stream = sseBody(
      [
        'event: final',
        `data: ${JSON.stringify(finalPayload)}`,
        '',
      ].join('\n'),
    );

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        body: stream,
      }),
    );

    const h = handlers();
    const response = await streamQuestion('Hello?', h);

    expect(response).toEqual(finalPayload);
    expect(h.final).toHaveBeenCalledWith(finalPayload);
    expect(h.streamError).not.toHaveBeenCalled();
  });
});
