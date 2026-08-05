# PC / Backend Code Review - 20260803T220222Z

## Current State

**Active Task**: `task-06-production-ingestion-cli`  
**Status**: PENDING, gate-red (exit code 1, compilation classification)  
**Last Green Attempt**: 2026-08-03T16:50:33.772689+00:00 (attempt 1)  
**Codex Decision**: REVISE

## Gate Diagnostic Summary

```
Classification: compilation
Exit code: 1
Fingerprint: 4a392a8e5f5586adce6a2fc2229fa3bc98004f42f2a8c78a824d69ddc108db21
Summary: Java compilation or test compilation failed.
Source paths named by current evidence:
- src/main/java/com/riansares/r4r/ingestion/KnowledgeIngestionOrchestration.java
```

## Issue Classification

The exact gate returns compilation failure (`exit code 1`). Codex REVISE decision from `codex-qwen3-extra-instructions.md` specifies mandatory corrections:

1. **Bean Registration**: Must install `BeanDefinitionRegistryPostProcessor` in `ApplicationContextInitializer` to replace `knowledgeIngestionService` bean definition after configuration-class registration but before singleton instantiation
2. **Exception Classification**: Use instanceof checks for typed exception families (SQLException, DataAccessException, ConnectException, SocketTimeoutException) and concrete Spring AI/Ollama transport exceptions across the cause chain – remove string package-prefix matching and unrelated OAuth2 class-name check
3. **Child Process Invocation**: Invoke `KnowledgeIngestionCli` directly as a child JVM under fixed timeout with valid corpus and environment variables; do not invoke via `R4rSpringAiRagApplication`
4. **Context Management**: Explicitly assert `context.close()` after using R4rSpringAiRagApplication in SERVLET mode

## Evidence of Current State

From git status:  
```
A  src/main/java/com/riansares/r4r/ingestion/IngestionConfiguration.java
A  src/main/java/com/riansares/r4r/ingestion/KnowledgeIngestionCli.java
A  src/main/java/com/riansares/r4r/ingestion/KnowledgeIngestionOrchestration.java
A  src/main/java/com/riansares/r4r/ingestion/KnowledgeIngestionResult.java
 M src/main/resources/application.yml
 A  src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionCliTest.java
 M src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionServiceIT.java
```

Ring worktree contains the base `KnowledgeIngestionService.java` implementation with proper typed exception wrapping (IllegalStateException for non-IllegalStateException exceptions, rethrow for IllegalStateException), but the production CLI task requires additional orchestration components not yet present in ring repo.

## Mandatory Corrections for Next Pass

Per codex-qwen3-extra-instructions.md:

1. Install `BeanDefinitionRegistryPostProcessor` from `ApplicationContextInitializer` to remove/replace knowledgeIngestionService with exact local Mockito mock before ordinary singleton creation
2. Use instanceof checks across cause chain: SQLException, DataAccessException, ConnectException, SocketTimeoutException and concrete Spring AI/Ollama exceptions; retain generic IllegalStateException and RuntimeException as exit 4
3. Invoke KnowledgeIngestionCli directly as child JVM under fixed timeout with valid corpus and RAG_* environment variables
4. For NONE-mode tests: exercise context-provided CLI orchestration, verify ingest exactly once, assert no servlet context/server, close explicitly
5. For A5 mode: use R4rSpringAiRagApplication with WebApplicationType.SERVLET and server.port=0, close in finally, call verifyNoInteractions only after closure

## Next Action

Apply all corrections from `codex-qwen3-extra-instructions.md` to PC worktree:

- Limit product edits to KnowledgeIngestionCliTest.java and narrowest correction in KnowledgeIngestionOrchestration.java
- Install BeanDefinitionRegistryPostProcessor in ApplicationContextInitializer
- Replace string-based exception classification with instanceof checks for typed families
- Direct child process invocation of KnowledgeIngestionCli with fixed timeout and valid environment variables
- Rerun exact gate: `bash -lc 'rm -rf target && ./scripts/task-gate.sh all && mvn -Dtest=KnowledgeIngestionCliTest -DfailIfNoTests=true test'`

## Acceptance Gates

- `./scripts/task-gate.sh task-06-production-ingestion-cli` must return exit 0
- Codex decision must be ACCEPT after corrections
- BeanDefinitionRegistryPostProcessor replaces knowledgeIngestionService bean definition after configuration-class registration but before singleton instantiation
- Full test coverage includes NONE-mode (no servlet, explicit close) and SERVLET-mode (server.port=0, no live PostgreSQL/Ollama)

## Avoid Repeating

Do not repeat ApplicationContextInitializer singleton registration, string-based exception classification (HikariConfig/OAuth2 class matching), or indirect child process invocation via R4rSpringAiRagApplication – Codex explicitly rejected these in codex-qwen3-extra-instructions.md for task-06-production-ingestion-cli.
