# Dependencies 1.1.1 Sync

## Context

`bluetape4k-dependencies` 1.1.0 was superseded by 1.1.1 after the artifact
availability audit found generated aliases for non-published mock web
application modules. This repository consumes the shared catalog and should not
pin around the BOM locally.

## Decision

Consume `bluetape4k-dependencies = "1.1.1"` through the standard shared-version
sync path. Keep the local catalog as a materialized copy of the source of truth
instead of adding repository-specific exclusions.

## Outcome

PR #57 aligned this repository to the 1.1.1 catalog and merged after CI passed.

## Verification

- GitHub PR #57 status checks passed before merge.
- Workspace-level `scripts/sync-shared-versions.py --workspace .. --check --summary`
  passed after the downstream PRs were merged.

## Future Guidance

When the shared catalog patch fixes publication availability, wait until Maven
Central `repo1` resolves the new version before rerunning downstream CI. If CI
then fails, treat the failure as repository-specific compatibility rather than
catalog availability.
