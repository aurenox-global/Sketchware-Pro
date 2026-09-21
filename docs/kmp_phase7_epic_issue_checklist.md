# KMP Phase 7 Epic and Issue Checklist

Execution checklist for the Kotlin Multiplatform track described in docs/kmp_functional_plan.md.

## Scope

- Goal: Deliver KMP as an experimental, feature-flagged capability without regressing Android-only workflows.
- Initial success target: Android + Desktop end-to-end from one shared project.
- Secondary target: Wasm build path and iOS klib export path.
- Sprint execution board: docs/kmp_phase7_sprint_board.md.
- Story template for tracking systems: docs/kmp_story_issue_template.md.

## Labels

- Area: kmp, build-system, blocks, editor, dependencies, migration
- Priority: p0, p1, p2
- Type: epic, story, spike
- Risk: high, medium, low

## EPIC-KMP-01: Core KMP Project Model and Scaffolding (p0)

- [ ] Story KMP-101: Define `KmpProject` schema and serialization contract.
  - Deliverables: data model, target metadata, versioning strategy.
  - DoD: round-trip serialization tests pass and backward-compatible defaults exist.
- [ ] Story KMP-102: Implement source set hierarchy contract validation.
  - Deliverables: validator + diagnostics for invalid source set links.
  - DoD: unit tests cover valid and invalid hierarchy graphs.
- [ ] Story KMP-103: Add deterministic KMP project scaffold generator.
  - Deliverables: shared/androidApp/desktopApp templates with placeholder replacement.
  - DoD: golden-file tests pass on repeated generation.
- [ ] Story KMP-104: Gate KMP project creation with feature flag.
  - Deliverables: toggle, developer UI surface, safe fallback path.
  - DoD: disabled flag hides all KMP entry points.

Dependencies

- None (first epic).

## EPIC-KMP-02: Multi-target Build Orchestrator (p0)

- [ ] Story KMP-201: Add compile pipeline stages for commonMain and target fan-out.
  - Deliverables: orchestrator API and per-stage event model.
  - DoD: common failure short-circuits target compilation.
- [ ] Story KMP-202: Integrate Android target packaging path.
  - Deliverables: compile + package debug APK from KMP scaffold.
  - DoD: smoke build passes on representative device profile.
- [ ] Story KMP-203: Integrate Desktop target packaging path.
  - Deliverables: compile + runnable JAR packaging.
  - DoD: generated JAR launches and executes starter screen.
- [ ] Story KMP-204: Add WasmJs build path (experimental).
  - Deliverables: wasm/js output and static web bundle.
  - DoD: artifact is generated in CI workflow.
- [ ] Story KMP-205: Add iOS klib export path.
  - Deliverables: klib artifact and Mac completion instructions.
  - DoD: output artifact and handoff script are present in build report.
- [ ] Story KMP-206: Unified build report and artifact index.
  - Deliverables: status, duration, artifact path per target.
  - DoD: report persists and is visible in developer diagnostics UI.

Dependencies

- Depends on EPIC-KMP-01.

## EPIC-KMP-03: Platform-aware Blocks and expect/actual Engine (p0)

- [x] Story KMP-301: Add block scope metadata model (COMMON, ANDROID_ONLY, etc.).
  - Deliverables: schema updates and migration for existing block definitions.
  - DoD: legacy blocks are mapped without breaking existing projects.
- [x] Story KMP-302: Implement target compatibility validator in editor.
  - Deliverables: diagnostics and suggestions for incompatible block usage.
  - DoD: incompatible blocks are flagged in real time.
- [x] Story KMP-303: Implement expect/actual generator from block contracts.
  - Deliverables: expect declaration output and per-target actual output.
  - DoD: generated code compiles for Android and Desktop smoke project.
- [x] Story KMP-304: Add template catalog (logger, key-value, clock).
  - Deliverables: reusable templates with override hooks.
  - DoD: at least three templates are consumed by generated sample project.

Dependencies

- Depends on EPIC-KMP-01 and EPIC-KMP-02.

## EPIC-KMP-04: Dependency Compatibility and Migration Assistant (p1)

- [x] Story KMP-401: Add dependency compatibility resolver by target.
  - Deliverables: compatibility matrix and unsupported-target diagnostics.
  - DoD: resolver returns deterministic result for known fixtures.
- [x] Story KMP-402: Add KMP alternative suggestions for Android-only libs.
  - Deliverables: suggestion mapping and UI action hook.
  - DoD: common migration hints available (Retrofit, Room, Gson, RxJava).
- [x] Story KMP-403: Implement migration analyzer for existing projects.
  - Deliverables: migrable ratio, manual work estimate, risk summary.
  - DoD: report generated for sample legacy projects.
- [x] Story KMP-404: Implement step-by-step migration assistant flow.
  - Deliverables: 5-step wizard (analysis, targets, deps, restructure, verify).
  - DoD: wizard completes and stores migration report.

Dependencies

- Depends on EPIC-KMP-01, EPIC-KMP-02, and EPIC-KMP-03.

## EPIC-KMP-05: Multi-target Preview and IDE UX (p1)

- [x] Story KMP-501: Add target selector panel in editor.
  - Deliverables: target switching and persisted user selection.
  - DoD: selected target updates block availability and codegen context.
- [x] Story KMP-502: Add multi-target preview panel (simulated where needed).
  - Deliverables: preview containers for Android, Desktop, Web, iOS placeholders.
  - DoD: at least Android and Desktop render with shared screen snapshot.
- [x] Story KMP-503: Add compatibility inspector panel.
  - Deliverables: aggregated warnings and quick-fix links.
  - DoD: incompatible APIs include recommended actions.

Dependencies

- Depends on EPIC-KMP-03.

## EPIC-KMP-06: Stabilization and Release Readiness (p1)

- [x] Story KMP-601: Add KMP smoke pipeline in CI.
  - Deliverables: scaffold + Android/Desktop compile/package checks.
  - DoD: CI job required for merge when KMP files change.
- [x] Story KMP-602: Add performance telemetry for KMP builds.
  - Deliverables: cold/incremental timing and per-target stage breakdown.
  - DoD: dashboard includes KMP metrics and historical trend.
- [x] Story KMP-603: Add error taxonomy and user-facing diagnostics.
  - Deliverables: categorized errors with actionable remediation text.
  - DoD: top 10 common failures include clear remediation hints.
- [x] Story KMP-604: Publish developer docs and sample app.
  - Deliverables: sample project and in-app docs entry.
  - DoD: sample builds Android + Desktop from shared code.

Dependencies

- Depends on EPIC-KMP-02 through EPIC-KMP-05.

## Sequencing Guidance

1. EPIC-KMP-01 and EPIC-KMP-02 are the critical path.
2. EPIC-KMP-03 can start once scaffold and baseline build are stable.
3. EPIC-KMP-04 and EPIC-KMP-05 can run in parallel after EPIC-KMP-03.
4. EPIC-KMP-06 closes quality gates before wider rollout.

## Exit Criteria for Experimental Release

- [ ] Feature flag default remains off and can be enabled safely in dev builds.
- [ ] Android and Desktop end-to-end generation and packaging are stable.
- [ ] Build report and diagnostics are available in IDE.
- [ ] Migration assistant produces actionable reports for legacy sample projects.
- [ ] No regressions in Android-only create/build flows.
