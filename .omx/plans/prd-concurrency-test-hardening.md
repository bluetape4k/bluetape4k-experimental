# PRD: Concurrency Test Hardening

## Task Statement
- Continue hardening repository/module tests with `bluetape4k-junit5` concurrency testers.

## Desired Outcome
- Add meaningful concurrency coverage to modules that expose cache, repository, or streaming behavior.
- Keep changes surgical and module-scoped.
- Verify each touched module with `./gradlew :<module>:test`.

## Scope
- In scope:
  - `:cache-lettuce-near`
  - `:exposed-r2dbc-spring-data`
  - `:exposed-lettuce`
  - next candidate modules with cache consistency or concurrent access behavior
- Out of scope:
  - whole-repo build
  - non-test refactors unless required for correctness

## Acceptance Criteria
- Each touched module has at least one added concurrency-focused test using `MultithreadingTester`, `SuspendedJobTester`, or `StructuredTaskScopeTester` when applicable.
- Runtime-incompatible `StructuredTaskScopeTester` coverage is conditionally skipped, not failing.
- Module-scoped tests pass after each change.
