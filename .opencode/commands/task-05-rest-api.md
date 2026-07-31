# Task 05 — REST API for the existing R4R backend

## Ownership

Owner: PC backend agent.

The laptop frontend agent must not modify Java backend paths.

## Objective

Expose the existing backend capabilities through a small, coherent REST API without
duplicating business logic or exposing persistence implementation details.

## Controller limit

Create no more than three REST controllers:

1. `RagController`
2. `KnowledgeController`
3. `ChunkingController`

Prefer fewer controllers when endpoints can be grouped coherently.

Do not create one controller per Java package.

## Required endpoints

### 1. RAG

Create an endpoint equivalent to:

`POST /api/rag/answers`

Request:

```json
{
  "question": "..."
}
