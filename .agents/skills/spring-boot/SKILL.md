---
name: spring-boot
description: Spring Boot conventions for the Local AI Assistant. Use when wiring beans, configuration, profiles, or HTTP controllers.
---

# Spring Boot

Authoritative detail: `docs/spec/10-code-quality-guidelines.md` section 2, and ADR `0001`. This skill is the checklist.

## When to Use

Use when adding or changing Spring beans, configuration binding, profiles, or the HTTP chat adapter in `assistant-app`.

## Rules

- Use constructor injection only. No field `@Autowired`, no setter injection in production code.
- Bind configuration with typed `@ConfigurationProperties` classes (`AssistantProperties`, `CountriesMcpProperties`). Avoid scattered `@Value`.
- Controllers are inbound adapters: parse request, call one application service, map the result. No routing, no source selection, no orchestration, no business decisions.
- Spring AI and Ollama details are allowed only in outbound adapters and configuration, behind `LlmPort`. They must not appear in domain or application code.
- Use Spring profiles and externalized configuration for local runtime settings.
- Do not hardcode secrets, URLs, ports, model names, or local paths. Assignment defaults live in documented config, not in logic.

## Patterns to Prefer

- A thin `ChatController` that maps `ChatRequest` to `UserQuestion`, calls `AssistantApplicationService`, and maps `AssistantAnswer` to `ChatResponse`.
- `@ConfigurationProperties` records with validation annotations for required values.
- Outbound adapters (`OllamaLlmAdapter`, `PgvectorRagAdapter`) implementing application ports.
- Profile-specific property files for local runtime variants.

## Patterns to Avoid

- Business logic, branching by question content, or tool selection inside a controller.
- Field injection or `new`-ing collaborators inside Spring-managed beans.
- Spring AI, MCP SDK, or JDBC types leaking into domain or application code.
- Reading raw `@Value` strings scattered across classes instead of typed properties.

## Verification Checklist

- Controllers contain no orchestration; the chat path is covered by a contract or integration test.
- All collaborators are constructor-injected.
- Configuration is bound through typed properties; no hardcoded secrets, URLs, ports, model names, or paths.
- Spring AI and Ollama types appear only inside adapters or configuration.
