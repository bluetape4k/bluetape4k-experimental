# bt4k Version Catalog Consumption

## Context

`bluetape4k-experimental` kept several dependency versions in its local
catalog even though those versions are already governed by
`bluetape4k-dependencies`.

## Decision

Import `io.github.bluetape4k:bluetape4k-version-catalog` as `bt4k` and use
`bt4kVersion(alias)` inside dependency management for shared leaf dependency
versions. Keep local aliases for repository coordinates and plugin/BOM train
versions that still need local plugin resolution.

## Outcome

Selected direct dependency aliases in `libs.versions.toml` are versionless and
their constraints are resolved from the shared `bt4k` catalog. Duplicate
generated constraints were removed so the build script has one constraint per
shared coordinate.

## Verification

- `git diff --check`
- `./gradlew help --no-daemon --no-configuration-cache`
- `./gradlew compileKotlin --no-daemon --no-configuration-cache`

## Future Guidance

Do not re-add local versions for dependencies already supplied by
`bluetape4k-dependencies`. Move the remaining plugin/BOM train duplicates only
when the central catalog exposes the required plugin aliases or the local build
script is migrated to consume those aliases safely.
