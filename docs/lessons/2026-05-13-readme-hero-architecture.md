# README Hero And Architecture Refresh

## Context

The experimental repository README was Korean-only and did not clearly separate
the stable user-facing English README from localized Korean documentation.

## Decision

Store the generated experimental workbench image in
`docs/assets/experimental-workbench.png`, make `README.md` English, and add
`README.ko.md` with the same structure.

## Outcome

The root README now explains the experimental proving-ground role, module
groups, architecture, and module-scoped validation rule in both locales.

## Verification

- Confirmed the generated asset exists as a PNG under `docs/assets`.
- Verified both README locales reference the shared image path.

## Future Guidance

Keep experimental work issue-driven and module-scoped; promote only documented
behavior into stable bluetape4k repositories.
