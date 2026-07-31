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
```

Response:

```json
{
  "answer": "Generated answer text",
  "abstained": false,
  "citations": [
    {
      "label": "[S1]",
      "source": "doc1.md",
      "headingPath": ["Section", "Subsection"],
      "ordinal": 0
    }
  ]
}
```

### Response fields

- `answer`: The generated answer text (empty string when abstained)
- `abstained`: Boolean indicating if the service abstained from answering
- `citations`: Array of citation objects with:
  - `label`: Citation label (e.g., "[S1]")
  - `source`: Source document path
  - `headingPath`: Array of heading names representing the document structure
  - `ordinal`: Zero-based position index

### Validation rules

- Return `400 Bad Request` if `question` is null, blank, or request body is missing/invalid

### Acceptance contract

1. **Success case**: When service returns a result with citations:
   - Response status is `200 OK`
   - `answer` contains the generated text
   - `abstained` is `false`
   - `citations` array matches the service citations exactly (label, source, headingPath, ordinal)

2. **Abstention case**: When service returns an abstention result:
   - Response status is `200 OK`
   - `answer` is empty string
   - `abstained` is `true`
   - `citations` is empty array

3. **Validation cases**:
   - Request with null question returns `400 Bad Request`
   - Request with blank/whitespace-only question returns `400 Bad Request`
   - Missing or invalid request body returns `400 Bad Request`

## Implementation notes

- Use the existing `CitedRagService` for business logic
- Map service `Citation` to API `Citation` preserving all fields
- Do not expose persistence implementation details
