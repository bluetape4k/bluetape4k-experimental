# Dependency Catalog Sync

## Context

`bluetape4k-dependencies` promoted Timefold Solver to 2.1.0 and Apache Fory to
0.17.0 as part of the central catalog update.

## Decision

Materialize the shared catalog change locally and run a non-test build check for
this experimental repository.

## Outcome

`gradle/libs.versions.toml` now carries the Timefold Solver 2.1.0 and Apache
Fory 0.17.0 versions from the central catalog.

## Verification

- `./gradlew build -x test --no-daemon`

The build completed with existing unrelated deprecation warnings.
