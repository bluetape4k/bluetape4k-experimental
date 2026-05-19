# Source-Verified README Diagrams

## Context

Experimental README diagrams contained placeholder labels, removed module names, and truncated text recovered from old Mermaid blocks.

## Decision

Replace generated filler cards with current module, dependency, and task names from the source tree, and lengthen short connector stems so arrows are visible.

## Verification

Check diagram labels against current module directories and Gradle task names, parse the SVG, and rerender PNGs from SVG.

## Future Guidance

For experimental repos, prefer smaller diagrams with exact active modules over speculative roadmap labels.
