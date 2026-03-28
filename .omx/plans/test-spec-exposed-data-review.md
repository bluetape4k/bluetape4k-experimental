# Test Spec: exposed data modules review hardening

## Verification targets

- `:exposed-duckdb:test`
- Additional touched modules via `:module:test`

## Required evidence

- New tests fail without the fix or meaningfully cover missing public contracts.
- No compilation errors in affected files.
- Targeted Gradle tests succeed after edits.
