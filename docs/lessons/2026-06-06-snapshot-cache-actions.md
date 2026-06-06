# Snapshot Cache Actions

## Context

The root Gradle configuration used a zero-second changing-module cache TTL, which
forces SNAPSHOT metadata revalidation on every configuration.

## Decision

Change the root changing-module cache TTL from zero seconds to one day.

## Outcome

Gradle can reuse mutable SNAPSHOT metadata during ordinary builds while still
refreshing it daily.

## Verification

- `actionlint .github/workflows/*.yml`
- `rg -n -- '--refresh-dependencies|cache-disabled: true' .github/workflows` -> no matches
- `./gradlew help --no-daemon`
- `git diff --check`

## Future Guidance

Use explicit dependency refresh only in dedicated post-publish freshness checks.
Ordinary CI, Nightly, and Examples workflows should rely on cached changing-module
metadata plus targeted warm-up when a test-only SNAPSHOT dependency needs it.
