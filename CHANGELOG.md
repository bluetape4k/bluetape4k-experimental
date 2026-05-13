# Changelog

All notable changes to `bluetape4k-experimental` are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This repository contains experimental modules and is not published as a stable
library line.

## [Unreleased]

### Added

- Root README hero image, Korean README, project-purpose, feature, and architecture documentation.
- `WIP.md` snapshot showing no currently assigned open issues.
- Lettuce-based read-through, write-through, and write-behind cache strategy experiments ([PR #1](https://github.com/bluetape4k/bluetape4k-experimental/pull/1)).
- Hospital appointment scheduling system experiment ([PR #3](https://github.com/bluetape4k/bluetape4k-experimental/pull/3)).
- Graph repository sync/suspend dual API experiment before graph extraction ([PR #5](https://github.com/bluetape4k/bluetape4k-experimental/pull/5)).
- Exposed CockroachDB JDBC support module and CockroachDB v26.1+ `WINDOW FRAME GROUPS` support ([PR #7](https://github.com/bluetape4k/bluetape4k-experimental/pull/7), [PR #8](https://github.com/bluetape4k/bluetape4k-experimental/pull/8)).
- CI and nightly workflows ([PR #12](https://github.com/bluetape4k/bluetape4k-experimental/pull/12)).

### Changed

- Root README language policy aligned: English in `README.md`, Korean in `README.ko.md`.
- Dependency governance, compatibility guard, Kover policy, and Dependabot maintenance landed through PR #18 through PR #27.
- Migrated `buildSrc` dependency declarations to `gradle/libs.versions.toml` and upgraded the Gradle wrapper to 9.5.0 ([PR #10](https://github.com/bluetape4k/bluetape4k-experimental/pull/10), [PR #11](https://github.com/bluetape4k/bluetape4k-experimental/pull/11)).
- Migrated graph modules out to the standalone `bluetape4k-graph` project ([PR #6](https://github.com/bluetape4k/bluetape4k-experimental/pull/6)).
- Switched Exposed dependencies to the `bluetape4k-exposed` group and integrated the `bluetape4k-dependencies` BOM ([PR #14](https://github.com/bluetape4k/bluetape4k-experimental/pull/14)).
- Test code migrated from Kluent to `bluetape4k-assertions` via `bluetape4k-junit5` ([PR #16](https://github.com/bluetape4k/bluetape4k-experimental/pull/16)).

### Fixed

- IDE diagnostics and code quality findings from early experimental modules ([PR #2](https://github.com/bluetape4k/bluetape4k-experimental/pull/2)).
- Removed `mavenLocal()` from build configuration ([PR #15](https://github.com/bluetape4k/bluetape4k-experimental/pull/15)).
