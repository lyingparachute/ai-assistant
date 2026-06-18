# Backend hygiene — M1 dependency version audit

Recorded: 2026-06-18 (M1 implementation run)

## Before → after (root `pom.xml`)

| Dependency | Before | After | Verification source |
| --- | --- | --- | --- |
| Spring Boot parent (`spring-boot-starter-parent`) | `3.5.9` | `3.5.15` | Maven Central `maven-metadata.xml` — latest `3.5.x` release |
| Spring AI BOM (`spring-ai.version`) | `1.1.2` | `1.1.8` | Maven Central `maven-metadata.xml` — latest `1.1.x` release |
| MCP SDK BOM (`mcp-sdk.version`) | `1.0.0` | `1.0.2` | Maven Central `maven-metadata.xml` — latest `1.0.x` release |
| jsoup (`jsoup.version`) | `1.19.1` | `1.22.2` | Maven Central `maven-metadata.xml` — latest `1.x` release |
| Java (`java.version`) | `21` | `21` | unchanged |
| versions-maven-plugin | absent (implicit `2.18.0`) | `2.18.0` in parent `pluginManagement` | `./mvnw versions:display-property-updates` |

## Skipped bumps (intentional)

| Property / artifact | Versions plugin suggests | Reason skipped |
| --- | --- | --- |
| `${spring-ai.version}` | `2.0.0` | Spring AI 2 requires Spring Boot 4 — platform migration, out of M1 scope |
| `${mcp-sdk.version}` | `2.0.0` | MCP SDK 2 ships with Spring AI 2 / Boot 4 — out of M1 scope |
| Spring Boot parent | `4.x` | Boot 4 migration deferred per `backend-hygiene.md` |
| MCP SDK `1.1.x` | `1.1.0`–`1.1.3` | Not on `1.0.x` line; plan locks MCP to `1.0.x` unless written exception |

## Audit commands (actual output summary)

### `./mvnw versions:display-property-updates -DallowSnapshots=false` (before bump)

```
${mcp-sdk.version} ................................... 1.0.0 -> 2.0.0
${spring-ai.version} ................................. 1.1.2 -> 2.0.0
```

(Boot parent and jsoup are not property-managed for the versions plugin on the parent POM; patch targets confirmed via Maven Central metadata XML.)

### Maven Central metadata (patch-line confirmation)

```
Boot 3.5.x latest:     3.5.15
Spring AI 1.1.x:       1.1.8
MCP SDK 1.0.x:         1.0.0, 1.0.1, 1.0.2
jsoup 1.x latest:      1.22.2
```

### `./mvnw versions:display-property-updates -DallowSnapshots=false` (after bump)

```
${mcp-sdk.version} ................................... 1.0.2 -> 2.0.0
${spring-ai.version} ................................. 1.1.8 -> 2.0.0
```

No further in-line patch updates remain within the approved lines.

## Verification

| Command | Result |
| --- | --- |
| `./mvnw -pl assistant-app,countries-mcp-server -am compile` | **PASS** (exit 0) |
| `./mvnw test` | **PASS** — `BUILD SUCCESS`, total time ~33s |

### Reactor test totals (`./mvnw test`)

| Module | Tests run | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `assistant-app` | 223 | 0 | 0 | 0 |
| `countries-mcp-server` | 30 | 0 | 0 | 0 |
| `e2e-tests` | 0 | — | — | — (no unit tests in default `test` phase) |

## Deviation from plan baseline

`docs/plans/backend-hygiene.md` baseline (2026-06-17) cited Spring Boot `3.5.12` as latest `3.5.x`. Maven Central metadata on 2026-06-18 lists `3.5.13`, `3.5.14`, and `3.5.15`; M1 applied **`3.5.15`** per the run scope (“latest compatible patch” on the `3.5.x` line).
