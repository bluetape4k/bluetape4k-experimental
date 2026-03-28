# PRD: exposed data modules review hardening

## Goal

Raise confidence in `data/exposed-*` modules by fixing correctness risks and adding focused regression coverage.

## Scope

- Review thinly tested modules first.
- Fix only code paths with concrete behavioral risk or missing contract validation.
- Add module-scoped tests for each touched path.

## Acceptance Criteria

- Each touched module has at least one new regression or contract test.
- Any discovered correctness issue is fixed with minimal diff.
- All touched modules pass `:module:test`.
