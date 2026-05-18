# WIP - bluetape4k-experimental

Snapshot: 2026-05-18 KST
Scope: open GitHub issues assigned to `debop`, created on or after 2026-01-01.
Open count: 1 issue.

## Recently Completed

- CI/Nightly workflows, Gradle 9.5.0 wrapper, version catalog migration, and Spring Boot 4 dependency alignment are merged.
- Graph modules were migrated to the standalone `bluetape4k-graph` repository.
- Exposed CockroachDB experiments and dependency/BOM alignment are merged.
- Kluent tests were migrated to `bluetape4k-assertions`.
- Dependency governance, compatibility guards, Kover policy, and Dependabot maintenance are merged through PR #18 through PR #27.
- Shared-version drift and central dependency governance updates are merged on
  2026-05-18.
- `exposed` artifactId rename tracking (#31) is closed.

## Current Direction

Java 25 workflow contract alignment.

This repository is the Kotlin 2.3 / Java 25 / Spring Boot 4 proving ground.
CI and Nightly should either run on JDK 25 or explicitly include a Java 25
verification lane before other experimental work is promoted.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | [#45](https://github.com/bluetape4k/bluetape4k-experimental/issues/45) CI and Nightly run on JDK 21 while the repo contract is Java 25 | S | Workflow runtime should validate Java 25 or clearly split runtime/toolchain coverage. |

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Build/CI maintenance | 1 | `#45` |
| Experimental feature | 1 | Wait for an assigned issue after the Java 25 workflow contract is clear. |
| Promotion work | 1 | Promote only after behavior and migration path are documented. |
