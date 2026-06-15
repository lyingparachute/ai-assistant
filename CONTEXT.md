# Local AI Assistant

This context defines the language used by the local Java AI Assistant recruitment task. It exists so documentation, code, tests, and reviews use the same terms.

## Language

**AI Assistant**:
A local chat application that answers user questions by combining model output with grounded knowledge sources.
_Avoid_: Bot, chatbot, agent

**Chat Interface**:
The user-facing entry point where a reviewer asks questions and reads assistant responses.
_Avoid_: Console, frontend, UI when the exact interface is not yet decided

**Demo Question**:
A question that must be asked against the running assistant to prove assignment behavior.
_Avoid_: Test question, sample prompt

**Demo Answer**:
An answer captured from the running assistant for a demo question.
_Avoid_: Expected answer when the value depends on current external data

**Knowledge Source**:
A source the assistant uses for grounded facts, such as product content, country data, or weather data.
_Avoid_: Memory, context, dataset

**RAG Knowledge**:
CDQ Fraud Guard product-page content that has been extracted, chunked, embedded, stored, and retrieved for answering product questions.
_Avoid_: Training data, model knowledge

**Country Facts**:
Country information returned by the REST Countries integration through the custom MCP server.
_Avoid_: Geography knowledge, model fact

**Weather Observation**:
Current weather returned by the local weather MCP integration. It carries a timestamp whose provenance is captured by the Observed Timestamp and Retrieval Timestamp terms below.
_Avoid_: Forecast, weather guess

**Observed Timestamp**:
The time the weather source reports the reading was measured. Used only when the weather source provides it.
_Avoid_: Using it as a synonym for retrieval timestamp

**Retrieval Timestamp**:
The time the weather adapter fetched the reading. Used when the source provides no observed timestamp. It must never be presented as an observed timestamp.
_Avoid_: Observation time, measured-at when the source did not provide one

**Tool**:
A callable external capability exposed to the assistant through MCP.
_Avoid_: Plugin, function, service when discussing assistant-facing capabilities

**Tool Result**:
The factual output returned by a tool for one assistant request.
_Avoid_: Model answer, generated result

**Source-Unavailable Response**:
An assistant response that names an unavailable required source and refuses to invent the missing fact.
_Avoid_: Fallback answer, best effort answer

**RAG**:
Retrieval-Augmented Generation using retrieved RAG knowledge as grounding context for model output.
_Avoid_: Search, fine-tuning

**Vector Database**:
The local store used for semantic retrieval of embedded chunks.
_Avoid_: Document database, cache

**pgvector**:
The PostgreSQL vector extension used as the project vector database.
_Avoid_: Postgres when the vector capability matters

**MCP**:
Model Context Protocol, used to expose tools to the assistant through a standard protocol.
_Avoid_: API when referring to the assistant-tool protocol

**Port**:
An application-owned contract for a required capability.
_Avoid_: Interface when the architectural boundary matters

**Adapter**:
An infrastructure implementation of a port or an inbound entry point into the application.
_Avoid_: Connector, client when the architectural boundary matters

**Ollama**:
The local model runtime used by the assistant.
_Avoid_: LLM provider, cloud model

**Embedding**:
A numeric representation of text used for semantic retrieval.
_Avoid_: Vector when describing the conversion result in the ingestion flow

**Chunk**:
A bounded piece of extracted product text stored with metadata for retrieval.
_Avoid_: Document, paragraph

**Hexagonal Architecture**:
The boundary style where the application owns ports and infrastructure supplies adapters.
_Avoid_: Layered architecture, clean architecture unless a later ADR chooses that term

**DDD**:
Domain-Driven Design, used here as discipline for shared language and explicit boundaries.
_Avoid_: Entity modeling when discussing language alignment

## Flagged Ambiguities

**Memory**:
Long-term and short-term memory are out of scope. Do not use memory to describe RAG knowledge, tool results, or chat history.

**Context**:
Use context only for this language file or model prompt context. Use knowledge source, RAG knowledge, or tool result when referring to grounded facts.

## Example Dialogue

Reviewer: "What is the temperature of the capital of Germany currently?"

Developer: "That is a demo question. The assistant should get country facts for Germany through the countries tool, then request a weather observation for the capital city."

Reviewer: "Can it answer from memory if weather is down?"

Developer: "No. It must return a source-unavailable response for the missing weather observation and avoid inventing a temperature."
