# Test Spec: Concurrency Test Hardening

## Verification Rules
- Run only module-scoped Gradle tests.
- Prefer direct evidence from affected suites.
- Treat runtime-missing `StructuredTaskScope` as skip/pending, not failure.

## Current Verified Modules
- `:cache-lettuce-near`
  - `MultithreadingTester` on blocking `putIfAbsent`
  - `SuspendedJobTester` on suspend `putIfAbsent`
  - `StructuredTaskScopeTester` guarded read-through consistency
- `:exposed-r2dbc-spring-data`
  - `SuspendedJobTester` on concurrent `save`
  - `MultithreadingTester` on parallel `findAllAsList`
  - `StructuredTaskScopeTester` guarded `streamAll`
- `:exposed-lettuce`
  - `MultithreadingTester` on concurrent repository `save`
  - `StructuredTaskScopeTester` guarded `findById` read-through

## Next Candidate
- `:hibernate-cache-lettuce`
  - focus on cross-session/cache consistency and concurrent access scenarios
