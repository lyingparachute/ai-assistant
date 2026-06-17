#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${ASSISTANT_E2E_BASE_URL:-http://localhost:8080}"
QUESTIONS_FILE="${DEMO_QUESTIONS_FILE:-${REPO_ROOT}/e2e-tests/src/test/resources/demo-questions.json}"
OUT_DIR="${REPO_ROOT}/docs/demo/capture"

require_command() {
  command -v "$1" >/dev/null 2>&1 \
    || { printf 'ERROR: required command not found: %s\n' "$1" >&2; exit 1; }
}

# python3 is a hard dependency: it parses demo-questions.json and builds/round-trips the chat
# JSON payloads. Fail loudly here instead of mid-capture with a cryptic interpreter error.
require_command curl
require_command python3

mkdir -p "$OUT_DIR"

read_questions() {
  python3 -c '
import json, sys
with open(sys.argv[1]) as f:
    data = json.load(f)
questions = data["questions"]
if not questions:
    sys.exit("No demo questions defined in " + sys.argv[1])
for q in questions:
    print(q["key"] + "\t" + q["question"] + "\t" + q["sourcePathKey"])
' "$QUESTIONS_FILE"
}

ask() {
  local label="$1"
  local question="$2"
  local source_path_key="$3"
  local outfile="$OUT_DIR/${label}.json"
  local payload answer
  payload=$(python3 -c 'import json,sys; print(json.dumps({"question": sys.argv[1]}))' "$question")
  answer=$(curl -sf -X POST "${BASE_URL}/api/chat" \
    -H 'Content-Type: application/json' \
    -d "$payload")
  python3 -c '
import json, sys
print(json.dumps({
    "expectedSourcePathKey": sys.argv[1],
    "response": json.loads(sys.argv[2]),
}, indent=2))
' "$source_path_key" "$answer" | tee "$outfile"
  echo
}

questions=$(read_questions)
if [ -z "$questions" ]; then
  echo "Refusing to capture: no demo questions read from ${QUESTIONS_FILE}" >&2
  exit 1
fi

echo "Capturing demo answers from ${BASE_URL} at $(date -u +"%Y-%m-%dT%H:%M:%SZ")"

index=0
while IFS=$'\t' read -r key question source_path_key; do
  index=$((index + 1))
  ask "$(printf '%02d-%s' "$index" "$key")" "$question" "$source_path_key"
done <<< "$questions"
