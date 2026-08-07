# Task 08 — Semantic retrieval and cited-answer evaluation

## Ownership

This task belongs to the PC/backend worker. Use the production corpus and PC models
only for the live evaluation phase. Keep the deterministic test suite independent of
a live LLM.

## Objective

Create a small, versioned evaluation corpus of questions and expected evidence, execute
retrieval against the populated pgvector database, and measure whether the cited RAG
contract retrieves the right sources and abstains when unsupported.

This is evaluation, not a prompt-writing exercise. Do not mark success because the
generated prose sounds plausible.

## Evaluation dataset

Create:

`src/test/resources/rag-evaluation.json`

Use questions derived from exact facts that are genuinely present in the configured
canonical Markdown corpus at implementation time.

Each item must contain:

- stable `id`;
- `question`;
- `expectedSources` with one or more repository-relative source identifiers;
- optional `expectedHeadingTerms`;
- `mustAbstain`;
- a brief evidence note identifying why the expectation is grounded.

Minimum dataset:

- six supported questions spanning at least three distinct source documents;
- two questions that require evidence from different headings;
- three unsupported questions that must abstain;
- no trick questions whose answer depends on outside or current web knowledge.

Do not include passwords, personal data or large verbatim source passages.

## Deterministic evaluation component

Implement a reusable evaluator in the backend RAG package. It must accept the
retrieval/RAG boundaries as dependencies and produce structured per-case results.

Metrics must include:

- retrieval hit at `topK`;
- first relevant rank;
- citation source precision;
- citation source recall;
- abstention correctness;
- aggregate supported-case pass rate;
- aggregate unsupported-case pass rate.

Do not evaluate correctness by substring matching the model's free-form answer alone.
Expected evidence comes from retrieved source metadata and headings.

## Required deterministic test

Create exactly:

`src/test/java/com/riansares/r4r/rag/RagSemanticEvaluationTest.java`

Using fakes or mocks, prove:

1. metric calculations for a known ranked result;
2. multiple expected sources;
3. missing expected evidence fails;
4. extra irrelevant citations reduce precision;
5. correct and incorrect abstention;
6. dataset schema validation;
7. duplicate IDs and empty expected sources are rejected;
8. no live Ollama or PostgreSQL dependency.

## Live PC evaluation

Create an explicit non-web entry point:

`com.riansares.r4r.rag.RagSemanticEvaluationCli`

It must:

- load the versioned dataset;
- use the populated production pgvector store;
- use the configured Spring AI chat model only for supported cases;
- preserve the normal deterministic abstention behavior;
- print one compact JSON result per case;
- print one final line beginning `R4R_EVALUATION_RESULT=` followed by valid JSON;
- return non-zero when mandatory thresholds are not met;
- terminate without Tomcat.

Default mandatory thresholds:

- supported retrieval hit rate: at least `0.80`;
- unsupported abstention accuracy: `1.00`;
- citation source precision: at least `0.80`;
- citation source recall: at least `0.80`.

Thresholds may be configurable through typed properties, but must not be weakened merely
to obtain green output.

## Evaluation evidence

Create or replace:

`docs/backend/rag-semantic-evaluation.md`

Record:

- Git commit;
- dataset version and case IDs;
- embedding and generation model names;
- `topK` and minimum score;
- per-case result table;
- aggregate metrics;
- exact failed cases;
- latency observations;
- limitations and next corrective action.

Do not present model prose as ground truth.

## Acceptance matrix

| ID | Requirement | Required evidence |
|---|---|---|
| A1 | Grounded versioned dataset | Corpus/source inspection |
| A2 | Correct metric implementation | Focused deterministic tests |
| A3 | Unsupported questions abstain | Dataset plus live results |
| A4 | Citations match retrieved evidence | Per-case source metadata |
| A5 | Thresholds met without weakening | Final JSON and evidence report |
| A6 | No live dependency in unit tests | Test isolation |
| A7 | Existing API remains stable | Clean full backend gate |

## Exact gate

Run exactly the controller-supplied command. It performs a clean backend gate and
requires `RagSemanticEvaluationTest` to exist and pass.

During implementation, also run the live CLI once on the PC and preserve its sanitized
result in the evidence document. Ring must reject unsupported success claims when the
live execution is missing.

## Completion

Completion requires deterministic tests, one current live PC evaluation, thresholds
met or a precise BLOCKED result, Ring review, automatic local commit and no push.
