import type { ApiErrorResponse, ChatResponse } from './types';

// PUBLIC_ASSISTANT_API_URL (see .env.example) is the configured source of the Assistant API
// base URL. DEFAULT_API_URL is only the local-dev fallback when that env var is unset.
const DEFAULT_API_URL = 'http://localhost:8080';

export function assistantApiUrl(): string {
  return import.meta.env.PUBLIC_ASSISTANT_API_URL ?? DEFAULT_API_URL;
}

export async function askQuestion(question: string): Promise<ChatResponse> {
  const response = await fetch(`${assistantApiUrl()}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question }),
  });

  if (!response.ok) {
    const errorBody = (await response.json().catch(() => null)) as ApiErrorResponse | null;
    const message = errorBody?.message ?? `Request failed with status ${response.status}`;
    throw new Error(message);
  }

  return (await response.json()) as ChatResponse;
}
