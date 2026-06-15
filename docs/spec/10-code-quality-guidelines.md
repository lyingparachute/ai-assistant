# Code Quality Guidelines

Practical implementation guidelines for the Local Java AI Assistant. These guidelines turn the architecture intent in `docs/spec/05-architecture.md`, the decisions in `docs/adr/`, and the language in `CONTEXT.md` into concrete, day-to-day rules.

This document is the single source of detail for code conventions. `AGENTS.md` states the contract; the local skills under `.agents/skills/` give focused checklists and reference this document instead of repeating it.

Scope note: this is documentation. The short snippets below are illustrative patterns, not production source files. No production Java code is added until the relevant implementation phase in `docs/spec/06-implementation-plan.md`.

## 1. Java Style

- Target idiomatic, modern Java. Use records for immutable value objects and DTOs. Use `final` for fields and locals that do not change.
- Construct objects completely from all required inputs. No empty construction followed by scattered mutation.
- Validate invariants in the constructor or a static factory. A constructed object is always valid.
- Do not return `null`. Use `Optional<T>` for optional return values, empty collections for "no elements", or an explicit no-result value object. `Optional` belongs on return types only, never on fields or parameters.
- Keep methods short and single-purpose. Prefer package-private helpers over a large public surface.
- No magic literals. Give every meaningful number or string a named constant in the module that owns the concept.
- Code self-documents. Comment only hidden invariants, framework or protocol constraints, and genuinely complex flow.
- Delete dead code. No commented-out blocks, unused imports, or unreachable branches.

Allowed:

```java
public record WeatherReport(Location location, Temperature temperature, WeatherTimestamp timestamp) {
    public WeatherReport {
        requireNonNull(location, "location");
        requireNonNull(temperature, "temperature");
        requireNonNull(timestamp, "timestamp");
    }
}

// WeatherTimestamp records provenance without null or Optional fields:
// an observed time from the weather source, or the adapter's retrieval time when
// the source provides no observation time. A retrieval time is never relabeled as observed.
public sealed interface WeatherTimestamp permits Observed, Retrieved {
    Instant value();
}
public record Observed(Instant value) implements WeatherTimestamp { /* validates non-null */ }
public record Retrieved(Instant value) implements WeatherTimestamp { /* validates non-null */ }
```

Disallowed:

```java
public class WeatherReport {
    private String location;     // mutable, no validation
    private Double temperature;  // null means "unknown" -> ambiguous
    // set piecemeal by callers, can exist in an invalid state
}
```

## 2. Spring Style

- Use constructor injection. No field injection (`@Autowired` on fields) and no setter injection in production code.
- Bind configuration with typed `@ConfigurationProperties` classes (for example `AssistantProperties`, `CountriesMcpProperties`). Avoid scattered `@Value`.
- Controllers are inbound adapters: parse the request, call one application service, map the result to a response. No routing decisions, no source selection, no orchestration.
- Spring AI and Ollama usage is allowed only in outbound adapters and configuration, behind `LlmPort` (ADR `0001`). Spring AI types must not appear in domain or application code.
- Use profiles for local runtime variants. Keep secrets and machine-specific values out of committed property files.

Disallowed:

```java
@RestController
class ChatController {
    @Autowired CountriesPort countries;   // field injection
    @Autowired WeatherPort weather;

    @PostMapping("/chat")
    String chat(@RequestBody String q) {
        if (q.contains("capital")) { ... }  // routing/business logic in controller
    }
}
```

## 3. Hexagonal Package Boundaries

Follow the module and package structure in `docs/spec/05-architecture.md`.

- Modules: `assistant-app`, `countries-mcp-server`, `e2e-tests`, and `shared-kernel` only when a concrete cross-module type exists.
- Package by business capability first (`question`, `orchestration`, `rag`, `tools`, `llm`), then by adapter (`adapters/inbound/http`, `adapters/outbound/ollama`).
- Dependency direction points inward:
  - inbound adapter -> application service -> port (interface owned by the application);
  - outbound adapter implements a port and is wired by configuration.
- Domain and application code must not import Spring, JDBC, HTTP clients, the MCP SDK, or Spring AI types.
- `assistant-app` may depend on `shared-kernel` when that conditional module exists. It must not depend on `countries-mcp-server` internals.
- `shared-kernel` is conditional and stays small: created only when a concrete cross-module type exists, holding only concepts stable across modules.

Allowed dependency: `OllamaLlmAdapter implements LlmPort`, with all Spring AI types confined to that adapter.

Disallowed dependency: a domain `AssistantAnswer` importing a Spring AI or HTTP response type.

## 4. Domain Model Rules

- Model the concepts named in `docs/spec/05-architecture.md` and `CONTEXT.md`: `UserQuestion`, `AssistantAnswer`, `AnswerSource`, `KnowledgeSnippet`, `CountryInfo`, `WeatherReport`, `ToolExecutionResult`, `ConversationTurn`.
- Wrap domain concepts in value objects rather than passing raw strings and primitives (country name, location, source identifier, temperature).
- Domain objects validate their own invariants: non-empty question text, weather reports requiring location and a `WeatherTimestamp` that distinguishes an observed time from a retrieval time, snippets requiring source URL and chunk metadata.
- `ConversationTurn` is request-local only. It is never persisted as memory and never used to answer a later request (ADR `0006`).
- Keep the domain free of framework concerns. No persistence annotations on domain value objects; map to persistence shapes inside the outbound adapter.

## 5. Error Handling Rules

Use the typed-outcome strategy from `docs/spec/05-architecture.md` section 10.

- Model expected source failures as typed application outcomes, not exceptions for control flow:
  - `SourceUnavailable` for Ollama, pgvector, countries MCP, weather MCP, REST Countries, or CDQ extraction failures;
  - `NoRelevantKnowledge` for RAG retrieval with no relevant snippets;
  - validation failures for empty questions, unsupported inputs, or malformed tool results.
- Exceptions represent unexpected system failures. Adapters may throw internally but must translate failures at the boundary into typed outcomes or a clear boundary exception.
- Never swallow exceptions. Never return an empty answer on error without surfacing the failure.
- Controllers must not hide failures or return empty answers.
- User-facing error messages name the unavailable source, avoid secrets, local paths, and raw stack traces, and never present a fabricated fact.
- Tool-facing error messages are precise enough for recovery (for example "country name is not recognized; provide an English country name or ISO code") and never expose upstream stack traces or raw provider payloads to the Chat Interface.

## 6. Configuration and Secrets Rules

- No secrets, API keys, credentials, model names, service URLs, ports, or local filesystem paths in production logic.
- Required configurable values are listed in `docs/spec/05-architecture.md` section 11: Ollama base URL and model name (default `qwen3:4b`), embedding settings, pgvector connection and retrieval settings, CDQ source URL, MCP server transport settings, REST Countries base URL, timeouts, and retry limits.
- Assignment-specific defaults may live in documented local configuration files when they are safe; they must not be embedded in business logic.
- Do not commit generated local state, databases, logs, extracted source dumps, or machine-specific files.

## 7. RAG Rules

Follow `docs/spec/05-architecture.md` section 8 and ADR `0003`.

- RAG covers CDQ Fraud Guard product-page content only. RAG is not memory.
- The pgvector outbound adapter owns the storage schema (a custom deterministic schema, embedding column `vector(768)` per ADR `0007`). Spring AI may supply embedding and model clients, but does not own the vector-store schema (ADR `0003`).
- Ingestion is staged and deterministic: fetch, extract, normalize, chunk, embed, store, report.
- Store chunk text, embedding, source URL, content hash, chunk index, and ingestion timestamp.
- Re-running ingestion is idempotent: it replaces the prior chunk set for a source (matched by source URL and content hash) in a single transaction rather than appending duplicates.
- Retrieval embeds the question, searches pgvector, applies a configured relevance threshold and top-k, and returns `KnowledgeSnippet` values.
- Keep prompt context lean: pass only relevant snippets and their source metadata, never a full page dump.
- If no relevant snippet passes the threshold, return an explicit no-result outcome. Do not lower the bar to force an answer.

## 8. MCP Rules

Follow `docs/spec/05-architecture.md` section 9 and ADR `0004`.

- MCP tools are semantic, assistant-facing capabilities, not one-to-one mirrors of REST Countries or the weather API.
- Tool names, descriptions, and JSON schemas must be understandable without external documentation.
- Tool outputs include only what the assistant needs for the answer, plus recovery hints for invalid input or unavailable sources.
- Use a consistent tool result envelope: a typed outcome carrying either the result data or a structured error with a recovery hint. Expected failures return a structured tool error; they do not crash the server.
- Recovery hints state what can be done next: available options, corrected ranges, or a "did you mean" suggestion for likely typos.
- The countries MCP server uses a small layered structure: core server factory, one tool class per semantic tool, schema definitions, REST Countries service classes, typed error helpers, configuration from environment or local profile.
- Lifecycle: initialize before emitting MCP notifications or logs, handle SIGINT/SIGTERM cleanly, and apply call timeouts. Local startup is declarative (for example a `.mcp.json` entry with command, args, env, and working directory).
- MCP SDK types stay inside adapters. The assistant application sees only `CountriesPort` and `WeatherPort`.
- Source routing for required demo questions is deterministic in application code. Any future tool loop needs a max-turn limit, timeout, cancellation, and typed `{ ok, error, hint }` results.

## 9. Testing Rules

Follow `docs/spec/07-test-strategy.md`.

- Test at boundaries with controlled adapters. Prefer integration-style tests over tests that mock every internal call.
- Unit tests cover deterministic domain behavior and policies; they never call Ollama, REST Countries, weather services, or pgvector.
- Contract tests cover the Assistant API payloads, MCP tool input/output and error shapes, REST Countries response mapping, the RAG repository read/write shape, and the local MCP configuration shape.
- Integration tests cover orchestration with controlled ports, the countries MCP server against a stubbed REST Countries server, the pgvector adapter against a real `pgvector/pgvector:pg17` container, and the chat endpoint with controlled ports.
- Cover happy paths and source-unavailable paths for every required source. No test invents a temperature, capital, or product fact when the source is unavailable.
- Tests do not require secrets, machine-specific paths, or uncontrolled network access.
- Maintain a small repeatable evaluation dataset for routing and grounding, with coverage of country-only, weather-only, combined, product, no-result, unavailable-source, and broad-synthesis cases. Prefer deterministic assertions for routing, required fields, and source-unavailable behavior; manual vibe checks alone are not enough.

## 10. Logging and Observability Rules

Follow `docs/spec/05-architecture.md` section 12.

- Log the major request steps: request received with correlation id, selected route or required sources, tool call started and completed, tool input validation failure with recovery hint, RAG retrieval count and source metadata, source-unavailable outcomes, and response composed.
- Logs must not expose secrets, credentials, local filesystem paths, or raw stack traces in normal operation.
- Failures in model access, vector retrieval, MCP calls, and ingestion must be distinguishable in logs.
- Emit enough structured log or trace evidence to reconstruct source routing, tool calls, RAG retrieval, and answer composition during demo review.
- Metrics and tracing platforms are optional for this task; structured logs are the baseline.

## 11. AI Usage Documentation Rules

Follow `docs/spec/03-acceptance-criteria.md` (AI Usage Explanation) and the README AI Usage section.

- Document material AI-assisted work under `docs/ai/` with the task or prompt summary, the agent role, the files affected, the human review performed, and the verification evidence.
- Do not present AI-generated runtime output as verified fact. Tool results, RAG output, and demo answers are captured from the running assistant, not authored by an agent.
- Keep the AI usage record honest about what was reviewed and what remains unverified.

## 12. Demo Evidence and Honesty Rules

Follow `docs/spec/08-demo-plan.md`.

- Final demo answers are captured only from the running assistant after implementation. They are not pre-written in documentation.
- Demo evidence shows the source path exercised for each required question, plus a small trace or log excerpt free of secrets and local paths.
- Weather answers include resolved location, temperature, a weather timestamp labeled as observed or retrieval time, and source status.
- If a required source is unavailable during capture, name the failure and do not substitute a value.

## 13. Quick Checklist Before Marking Work Done

- The change matches the relevant spec and ADR, and any divergence updated the document.
- Boundaries hold: no infrastructure types in domain or application code.
- Value objects are complete and self-validating; no `null` returns; no swallowed exceptions.
- Configuration is externalized; no hardcoded secrets, URLs, ports, model names, or local paths.
- Tests cover the new behavior including its source-unavailable path, and the focused test output was actually observed.
- No fabricated tool results, RAG results, or demo answers were introduced.
