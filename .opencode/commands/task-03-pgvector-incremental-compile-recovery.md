# Task 03 incremental compile recovery

While `compile` or `testCompile` is red:

1. keep package/imports, class declaration, constants, fields, constructors,
   annotations and public signatures active;
2. fix only the first compiler error in one method;
3. use a bounded patch, never whole-file `write` for an existing Java class;
4. reopen the method and run the matching compile command immediately;
5. advance only after that error disappears.

A temporary method-body quarantine is permitted only when syntax corruption prevents
compilation. Preserve the signature and throw a clearly marked
`UnsupportedOperationException`. Quarantine one method at a time; do not run the
official gate until every quarantine/stub is removed.

Do not disable tests/plugins, change dependencies to hide errors, exclude classes or
create new abstractions while compilation is red. A tool-schema error such as
`Missing key: content` must switch to a small patch, not repeat a whole-file write.

IntelliJ's `Unable to resolve table` inspection is not a Java error unless Maven,
Flyway or PostgreSQL reproduces it.
