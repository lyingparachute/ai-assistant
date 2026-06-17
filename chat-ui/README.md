# Chat UI

Astro single-page Chat Interface for the Local AI Assistant. Calls the Assistant API (`POST /api/chat`) from the browser.

## Prerequisites

- Node.js 22.12+ (see `engines` in `package.json`)
- Running `assistant-app` backend on port 8080 (default)

## Setup

```bash
cd chat-ui
npm install
cp .env.example .env
```

## Development

Start the Astro dev server (default `http://localhost:4321`):

```bash
npm run dev
```

Set the backend URL if needed:

```bash
PUBLIC_ASSISTANT_API_URL=http://localhost:8080 npm run dev
```

## Behavior

- Single-turn only: each submit sends the current question text; no chat history or localStorage.
- Renders `answerText` and structured `sources[]` from the API without re-deriving facts.
- Weather sources show location, temperature, and timestamp labeled Observed or Retrieved.

## Build

```bash
npm run build
npm run preview
```
