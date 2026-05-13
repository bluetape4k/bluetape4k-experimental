# WIP - bluetape4k-experimental

Snapshot: 2026-05-13 KST
Scope: open GitHub issues assigned to `debop`, created on or after 2026-01-01.
Open count: 0 issues.

## Recently Completed

- CI/Nightly workflows, Gradle 9.5.0 wrapper, version catalog migration, and Spring Boot 4 dependency alignment are merged.
- Graph modules were migrated to the standalone `bluetape4k-graph` repository.
- Exposed CockroachDB experiments and dependency/BOM alignment are merged.
- Kluent tests were migrated to `bluetape4k-assertions`.
- Dependency governance, compatibility guards, Kover policy, and Dependabot maintenance are merged through PR #18 through PR #27.

## Current Direction

There is no assigned open issue at this snapshot. Keep future work issue-driven,
module-scoped, and clearly marked as experimental before promoting anything to a
stable bluetape4k repository.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | none | - | Create or assign a focused issue before implementation. |

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Experimental feature | 1 | Wait for an assigned issue. |
| Build/CI maintenance | 1 | Handle only concrete failures from CI/Nightly. |
| Promotion work | 1 | Promote only after behavior and migration path are documented. |
