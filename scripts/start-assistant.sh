#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$REPO_ROOT/.local/logs"
BACKEND_PORT="${ASSISTANT_BACKEND_PORT:-8080}"
FRONTEND_PORT="${CHAT_UI_PORT:-4321}"
BACKEND_URL="http://localhost:${BACKEND_PORT}"
FRONTEND_URL="http://localhost:${FRONTEND_PORT}"
COUNTRIES_TARGET_DIR="$REPO_ROOT/countries-mcp-server/target"

# Load local secrets (gitignored) so keys reach the assistant JVM and the MCP subprocesses.
if [[ -f "$REPO_ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$REPO_ROOT/.env"
  set +a
fi

BACKEND_PID=""
FRONTEND_PID=""

info() {
  printf '==> %s\n' "$*"
}

warn() {
  printf 'WARNING: %s\n' "$*" >&2
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

port_in_use() {
  local port="$1"
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    return 0
  fi
  return 1
}

wait_for_port() {
  local name="$1"
  local port="$2"
  local pid="$3"
  local timeout_seconds="${4:-120}"
  local elapsed=0

  while ! port_in_use "$port"; do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      fail "$name exited before port $port became ready. See logs in $LOG_DIR"
    fi
    if (( elapsed >= timeout_seconds )); then
      fail "Timed out waiting for $name on port $port. See logs in $LOG_DIR"
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
}

kill_tree() {
  local pid="$1"
  if [[ -z "$pid" ]]; then
    return 0
  fi
  if kill -0 "$pid" >/dev/null 2>&1; then
    kill "$pid" >/dev/null 2>&1 || true
    pkill -P "$pid" >/dev/null 2>&1 || true
  fi
}

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM

  if [[ -n "$FRONTEND_PID" ]] || [[ -n "$BACKEND_PID" ]]; then
    info "Stopping assistant processes..."
  fi
  kill_tree "$FRONTEND_PID"
  kill_tree "$BACKEND_PID"

  if (( exit_code != 0 )); then
    warn "Assistant stopped with errors. Logs: $LOG_DIR"
  fi
}

pause_on_failure() {
  if [[ "${START_ASSISTANT_NO_PAUSE:-}" == "1" ]]; then
    return 0
  fi
  if [[ -t 0 ]] && [[ -t 1 ]]; then
    printf '\nPress Enter to close this window...'
    read -r _
  fi
}

require_command() {
  local name="$1"
  command -v "$name" >/dev/null 2>&1 || fail "Required command not found: $name"
}

check_prerequisites() {
  require_command java
  require_command npm
  require_command lsof

  if port_in_use "$BACKEND_PORT"; then
    fail "Port $BACKEND_PORT is already in use. Stop the other process or set ASSISTANT_BACKEND_PORT."
  fi
  if port_in_use "$FRONTEND_PORT"; then
    fail "Port $FRONTEND_PORT is already in use. Stop the other process or set CHAT_UI_PORT."
  fi
}

warn_if_dependency_missing() {
  if ! curl -sf --max-time 2 "http://localhost:11434/api/tags" >/dev/null 2>&1; then
    warn "Ollama is not reachable at http://localhost:11434 — synthesis and RAG embedding may fail."
  fi
  if ! nc -z localhost 5432 >/dev/null 2>&1; then
    warn "PostgreSQL is not listening on localhost:5432 — RAG answers will be source-unavailable until pgvector is running."
  fi
  if [[ -z "${WEATHER_API_KEY:-}" ]] || [[ -z "${WEATHER_API_URL:-}" ]]; then
    warn "WEATHER_API_KEY / WEATHER_API_URL are not set — weather answers will be source-unavailable."
  fi
}

# Resolve the newest non-sources countries jar so the version string is not pinned in the launcher.
# Exports COUNTRIES_MCP_JAR; application.yml reads it via ${COUNTRIES_MCP_JAR:...}.
newest_countries_jar() {
  ls -t "$COUNTRIES_TARGET_DIR"/countries-mcp-server-*.jar 2>/dev/null \
    | grep -v -- '-sources\.jar$' \
    | grep -v -- '-javadoc\.jar$' \
    | head -1
}

ensure_countries_jar() {
  local jar
  # `|| true`: a zero-match glob exits non-zero; the empty-match build branch below handles it,
  # so do not let it abort here regardless of how main is invoked.
  jar="$(newest_countries_jar || true)"
  if [[ -z "$jar" ]]; then
    info "Building countries MCP server jar (first run)..."
    (cd "$REPO_ROOT" && ./mvnw -q -pl countries-mcp-server -am package -DskipTests)
    jar="$(newest_countries_jar || true)"
  fi
  [[ -n "$jar" ]] || fail "Countries MCP jar was not produced under $COUNTRIES_TARGET_DIR"
  export COUNTRIES_MCP_JAR="$jar"
}

ensure_chat_ui_ready() {
  if [[ ! -f "$REPO_ROOT/chat-ui/.env" ]] && [[ -f "$REPO_ROOT/chat-ui/.env.example" ]]; then
    cp "$REPO_ROOT/chat-ui/.env.example" "$REPO_ROOT/chat-ui/.env"
    info "Created chat-ui/.env from .env.example"
  fi
  if [[ ! -d "$REPO_ROOT/chat-ui/node_modules" ]]; then
    info "Installing Chat Interface dependencies (first run)..."
    (cd "$REPO_ROOT/chat-ui" && npm install --silent)
  fi
}

open_browser() {
  local url="$1"
  if [[ "$(uname -s)" == "Darwin" ]] && command -v open >/dev/null 2>&1; then
    open "$url"
    return 0
  fi
  if command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$url"
    return 0
  fi
  info "Open $url in your browser."
}

start_backend() {
  info "Starting Assistant API on $BACKEND_URL ..."
  mkdir -p "$LOG_DIR"
  (
    cd "$REPO_ROOT"
    ./mvnw -q -pl assistant-app spring-boot:run
  ) >"$LOG_DIR/backend.log" 2>&1 &
  BACKEND_PID=$!
  wait_for_port "Assistant API" "$BACKEND_PORT" "$BACKEND_PID" 180
  info "Assistant API is ready ($BACKEND_URL)"
}

start_frontend() {
  info "Starting Chat Interface on $FRONTEND_URL ..."
  (
    cd "$REPO_ROOT/chat-ui"
    npm run dev -- --port "$FRONTEND_PORT" --host localhost
  ) >"$LOG_DIR/frontend.log" 2>&1 &
  FRONTEND_PID=$!
  wait_for_port "Chat Interface" "$FRONTEND_PORT" "$FRONTEND_PID" 60
  info "Chat Interface is ready ($FRONTEND_URL)"
}

main() {
  trap cleanup EXIT INT TERM

  cd "$REPO_ROOT"
  info "Local Java AI Assistant — starting backend and Chat Interface"
  check_prerequisites
  warn_if_dependency_missing
  ensure_countries_jar
  ensure_chat_ui_ready
  start_backend
  start_frontend

  info "Assistant is running."
  printf '    Backend:  %s\n' "$BACKEND_URL"
  printf '    Chat UI:  %s\n' "$FRONTEND_URL"
  printf '    Logs:     %s\n' "$LOG_DIR"
  printf '    Stop:     press Ctrl+C in this terminal\n'

  open_browser "$FRONTEND_URL"

  while kill -0 "$BACKEND_PID" >/dev/null 2>&1 && kill -0 "$FRONTEND_PID" >/dev/null 2>&1; do
    sleep 1
  done

  fail "One of the assistant processes exited unexpectedly. Check $LOG_DIR"
}

if ! main; then
  pause_on_failure
  exit 1
fi
