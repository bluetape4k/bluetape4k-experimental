# Dependencies 1.1.4 Sync

## Context

`bluetape4k-dependencies` 1.2.0 release preparation requires downstream
catalog consumers to be free of shared-version drift before the central BOM CI
can pass on `develop`.

## Decision

Align the local `bluetape4k-dependencies` catalog reference to the latest
published `1.1.4` baseline. Do not consume `1.2.0` until that BOM has been
published and is visible from Maven Central.

## Outcome

The repository now matches the central shared-version source of truth for the
current release-train preflight.

## Verification

Validated from `bluetape4k-dependencies` with `sync-shared-versions.py
--workspace /Users/debop/work/bluetape4k --write --check --summary`.
