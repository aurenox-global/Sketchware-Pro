# Module Boundaries and Ownership Map

## Scope

This document defines current architecture boundaries and ownership responsibilities for the repository.

Current Gradle module inventory:

- :app

The repository is still single-module at Gradle level. Boundaries are currently enforced at package/layer level.

## Enforced Boundaries (Current)

Source of truth for automated checks:

- scripts/check_architecture_boundaries.sh

Rules currently enforced in CI:

1. Core packages must not import UI/legacy layers.
2. AI core package must not import Activities.

Current guarded package roots:

- app/src/main/java/pro/sketchware/featureflags
- app/src/main/java/pro/sketchware/metrics
- app/src/main/java/pro/sketchware/ai

## Ownership Map

| Area | Primary ownership group | Guarded package roots | Notes |
|---|---|---|---|
| Feature flags and rollout controls | Core Platform | pro.sketchware.featureflags | Must remain UI-independent |
| Metrics, KPI, and release gates | Core Platform | pro.sketchware.metrics | Must remain UI-independent |
| AI core orchestration | AI Platform | pro.sketchware.ai | Must not depend on Activities |
| Editor and LSP integrations | Editor Platform | pro.sketchware.lsp, editor activities | Feature-flagged rollout |
| Debugger and profiling foundations | Runtime Tooling | pro.sketchware.debugger | Keep deterministic and thread-safe contracts |
| Plugin runtime and security | Plugin Runtime | pro.sketchware.plugins | Security scoring and static analysis chain |

## Ownership Responsibilities

1. Any boundary change requires updates in scripts/check_architecture_boundaries.sh and corresponding tests/docs.
2. Any new package under pro.sketchware should be mapped to a primary ownership group here.
3. Boundary regressions are release blockers in CI.

## Next Increment

When moving to multi-module Gradle structure, this map should be extended with:

- module-level owners
- allowed dependency matrix (module -> module)
- mandatory CODEOWNERS alignment
