# 2026-05-18 — Experimental WIP audit and Java 25 CI contract

## Context

The WIP file still had no assigned open issues after the latest dependency
governance updates. The repository documents itself as a Kotlin 2.3, Java 25,
and Spring Boot 4 proving ground.

## Decision

Register #45 for the workflow contract gap: CI and Nightly install JDK 21 while
the README, AGENTS.md, and Gradle toolchains all describe Java 25 as the target
runtime/toolchain.

## Outcome

`WIP.md` now lists #45 as the next build/CI maintenance item before new
experimental features or promotion work.

## Verification

- `gh issue list --state open --assignee debop` returned one open issue.
- `gh issue view 45` confirmed #45 is open, labelled `bug`, `github_actions`,
  and `java`, and assigned to `debop`.
- `rg` confirmed the workflow JDK 21 setting and the Gradle Java 25 toolchain
  setting.

## Future Agents

For experimental modules, check workflow runtime JDK, Gradle toolchain JDK, and
README/AGENTS prerequisites together. A toolchain compile can hide a workflow
runtime mismatch.
