# Task 03 focused recovery

When the gate is red, use the controller-generated diagnostic bundle. Ring receives
the complete log; OpenCode receives the classification, source list and bounded tail.

Repair order:

1. compilation/test-compilation errors;
2. Spring context/configuration errors;
3. Flyway/schema errors;
4. focused failing test assertions;
5. full task gate.

Read only the named file, direct symbol definitions and focused CodeGraph callers.
Do not rewrite a class, add helpers or broaden tests while a smaller method-level fix
can compile. Reopen the changed method before compiling.

Commands:

- production compile: `mvn -DskipTests compile`
- test compile: `mvn -DskipTests test-compile`
- focused unit: `mvn -Dtest=Class#method test`
- official evidence: `./scripts/task-gate.sh task-03-pgvector`

Direct `mvn install` does not start `postgres-test`; use the official gate for
integration evidence.
