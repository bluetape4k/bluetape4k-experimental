# Kover Coverage Policy

## Current Status

`bluetape4k-experimental` does not enforce Kover verification bounds.

## Policy

Status: documented exception.

This repository is unpublished experimental work on newer Kotlin, Java, and
Spring Boot baselines. Coverage is not a release gate until a module is promoted
from experimental to a published library.

## Threshold Plan

- Keep tests compiling and running in CI/Nightly.
- Before publishing a module, measure Kover line coverage and document the
  observed baseline.
- Use coverage reports to identify regressions; do not introduce a failing
  threshold as the default enforcement mechanism.

## CI/Nightly Contract

CI/Nightly currently provide build and test signals. Coverage reports may be
added for visibility, but they must remain informational unless a future issue
explicitly reintroduces a gate.
