# KMP Sprint 1 Issue Batch

Ready-to-copy issue drafts for Sprint 1 stories:
- KMP-101
- KMP-102
- KMP-103
- KMP-104
- KMP-201
- KMP-202

## KMP-101: Define KmpProject schema and serialization contract

### Summary

- Story ID: KMP-101
- Epic: EPIC-KMP-01
- Priority: p0
- Estimate: 5 points

### Problem

There is no canonical schema for KMP projects in the current codebase. Without a stable model and serialization rules, scaffold generation and build orchestration cannot rely on deterministic inputs.

### Scope

- In scope: data model, defaults, schema version field, serialization and deserialization tests.
- Out of scope: scaffold generation and build execution.

### Implementation Tasks

- [ ] Add KmpProject domain model and target metadata objects.
- [ ] Add schema versioning and backward-compatible defaults.
- [ ] Add serialization and deserialization adapters.
- [ ] Add round-trip tests with fixtures.

### Deliverables

- KmpProject model contract with target/source set metadata.
- Stable serialized format with version field.

### Definition of Done

- [ ] Round-trip serialization tests pass for all supported targets.
- [ ] Missing optional fields are filled by backward-compatible defaults.
- [ ] Invalid schema produces explicit diagnostics.

### Acceptance Tests

- [ ] Unit tests added for model serialization and validation.
- [ ] Fixture-based tests for backward compatibility.
- [ ] Manual verification by creating and loading at least one fixture.

### Telemetry and Diagnostics

- Metrics/events: kmp_model_load_ok, kmp_model_load_error.
- Error categories: schema_error, version_error, parse_error.
- Required logs: schema version, invalid field names, fallback-default usage.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: disable flag and route project creation to current Android-only model.

### Dependencies

- Blocked by: none.
- Blocks: KMP-103, KMP-201.

### Risks and Mitigations

- Risk: future model evolution breaks old projects.
- Mitigation: schema version + migration defaults + fixture regression tests.

### Verification Notes

Attach unit test output and one serialized fixture before/after round-trip.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.

## KMP-102: Implement source set hierarchy contract validation

### Summary

- Story ID: KMP-102
- Epic: EPIC-KMP-01
- Priority: p0
- Estimate: 3 points

### Problem

Invalid source set hierarchies can silently propagate and fail late during compilation. A contract validator is needed to fail fast and provide actionable diagnostics.

### Scope

- In scope: hierarchy validator, cycle detection, missing-parent detection, diagnostics model.
- Out of scope: Gradle script generation.

### Implementation Tasks

- [ ] Implement source set hierarchy validator.
- [ ] Add checks for cycles and unknown parent nodes.
- [ ] Add diagnostics with machine-readable codes.
- [ ] Add unit tests for valid and invalid hierarchies.

### Deliverables

- Hierarchy validation component.
- Structured diagnostics for invalid source set graphs.

### Definition of Done

- [ ] Validator rejects cyclic and orphan source set definitions.
- [ ] Validator accepts baseline hierarchy for Android and Desktop.
- [ ] Diagnostics include source set name and reason.

### Acceptance Tests

- [ ] Unit tests for valid graph and at least 5 invalid graph patterns.
- [ ] Integration test that blocks downstream compile step on invalid graph.
- [ ] Manual verification from a malformed fixture.

### Telemetry and Diagnostics

- Metrics/events: kmp_hierarchy_validate_ok, kmp_hierarchy_validate_error.
- Error categories: hierarchy_cycle, hierarchy_missing_parent, hierarchy_unknown_node.
- Required logs: invalid edge list and normalized hierarchy summary.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: bypass validator only under internal debug override.

### Dependencies

- Blocked by: KMP-101.
- Blocks: KMP-103, KMP-201.

### Risks and Mitigations

- Risk: false positives on valid advanced hierarchies.
- Mitigation: allow-list strategy for known valid patterns + fixture expansion.

### Verification Notes

Attach validator test output and one blocked-build trace showing explicit diagnostic.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.

## KMP-103: Add deterministic KMP project scaffold generator

### Summary

- Story ID: KMP-103
- Epic: EPIC-KMP-01
- Priority: p0
- Estimate: 5 points

### Problem

There is no KMP scaffold path that creates shared, Android, and Desktop modules in a deterministic layout. This blocks repeatable setup and reliable CI smoke checks.

### Scope

- In scope: project template generation, module/file layout, placeholder replacement, deterministic output.
- Out of scope: target compilation.

### Implementation Tasks

- [ ] Add scaffold template set for shared, androidApp, and desktopApp.
- [ ] Add deterministic placeholder expansion for package, app name, and versions.
- [ ] Add overwrite safety rules and diagnostics.
- [ ] Add golden-file tests for generated outputs.

### Deliverables

- KMP scaffold generator with deterministic file output.
- Golden test suite for repeatability.

### Definition of Done

- [ ] Two consecutive generations from same input produce byte-identical outputs.
- [ ] Scaffold includes shared, androidApp, desktopApp modules and baseline Gradle files.
- [ ] Invalid or conflicting path state yields explicit error diagnostics.

### Acceptance Tests

- [ ] Golden tests for deterministic generation.
- [ ] Smoke test for scaffold creation on clean workspace path.
- [ ] Manual creation test from IDE flow with feature flag on.

### Telemetry and Diagnostics

- Metrics/events: kmp_scaffold_generate_ok, kmp_scaffold_generate_error.
- Error categories: template_error, path_conflict, placeholder_error.
- Required logs: target path, module list, generation duration.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: hide KMP scaffold option and preserve existing project wizard.

### Dependencies

- Blocked by: KMP-101, KMP-102.
- Blocks: KMP-201, KMP-202.

### Risks and Mitigations

- Risk: nondeterministic timestamps/order in generated files.
- Mitigation: sorted template traversal and timestamp-neutral golden assertions.

### Verification Notes

Attach generated tree diff for run 1 vs run 2 and golden test report.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.

## KMP-104: Gate KMP project creation with feature flag

### Summary

- Story ID: KMP-104
- Epic: EPIC-KMP-01
- Priority: p0
- Estimate: 2 points

### Problem

KMP should be experimental during early rollout. Without a strict feature flag gate, unfinished paths may affect standard Android-only user flows.

### Scope

- In scope: flag creation, UI wiring, default-off behavior, fallback routing.
- Out of scope: role-based rollout and remote config.

### Implementation Tasks

- [ ] Add KMP_EXPERIMENTAL_ENABLE feature flag key.
- [ ] Wire project creation UI visibility to the flag.
- [ ] Add fallback routing to Android-only wizard when disabled.
- [ ] Add tests for enabled and disabled scenarios.

### Deliverables

- Feature flag guard covering all KMP entry points in project creation.
- Regression tests for Android-only flow unaffected by disabled flag.

### Definition of Done

- [ ] KMP entry points are hidden when flag is off.
- [ ] KMP entry points are available when flag is on.
- [ ] Android-only project creation behavior remains unchanged.

### Acceptance Tests

- [ ] UI behavior tests for flag off/on.
- [ ] Regression smoke test for Android-only create/build.
- [ ] Manual verification toggling flag from developer settings.

### Telemetry and Diagnostics

- Metrics/events: kmp_flag_enabled, kmp_flag_disabled, kmp_entrypoint_blocked.
- Error categories: flag_state_error.
- Required logs: flag value and entry point name.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: keep default OFF and remove KMP entry point exposure.

### Dependencies

- Blocked by: none.
- Blocks: all KMP runtime onboarding stories.

### Risks and Mitigations

- Risk: hidden deep links bypass UI gate.
- Mitigation: validate flag server-side in feature router, not only UI layer.

### Verification Notes

Attach UI capture for both flag states and routing logs.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.

## KMP-201: Add compile pipeline stages for commonMain and target fan-out

### Summary

- Story ID: KMP-201
- Epic: EPIC-KMP-02
- Priority: p0
- Estimate: 3 points

### Problem

The current build flow does not model commonMain-first compilation and controlled target fan-out. KMP needs stage orchestration and clear behavior when common compilation fails.

### Scope

- In scope: stage model, commonMain first, target fan-out orchestration hooks, fail-fast behavior.
- Out of scope: full packaging for all targets.

### Implementation Tasks

- [ ] Add orchestrator stage model with start/finish/error events.
- [ ] Implement commonMain-first compile phase.
- [ ] Implement target fan-out trigger only after common success.
- [ ] Add tests for fail-fast and event ordering.

### Deliverables

- Build pipeline stage orchestration for KMP flow.
- Event stream for stage-level diagnostics.

### Definition of Done

- [ ] commonMain stage always runs before target stages.
- [ ] target stages are not executed when commonMain fails.
- [ ] stage event order is deterministic and test-covered.

### Acceptance Tests

- [ ] Unit tests for stage transitions and fail-fast behavior.
- [ ] Integration smoke run showing event timeline.
- [ ] Manual validation with forced commonMain failure.

### Telemetry and Diagnostics

- Metrics/events: kmp_stage_start, kmp_stage_finish, kmp_stage_error.
- Error categories: common_compile_error, stage_order_error.
- Required logs: stage name, duration, error summary.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: bypass KMP orchestrator and use existing Android build path.

### Dependencies

- Blocked by: KMP-101, KMP-102, KMP-103.
- Blocks: KMP-202, KMP-203, KMP-206.

### Risks and Mitigations

- Risk: race conditions in parallel target fan-out.
- Mitigation: serialized stage controller and bounded executor for fan-out.

### Verification Notes

Attach orchestrator event timeline and fail-fast test output.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.

## KMP-202: Integrate Android target packaging path

### Summary

- Story ID: KMP-202
- Epic: EPIC-KMP-02
- Priority: p0
- Estimate: 3 points

### Problem

Generated KMP projects do not yet package Android debug artifacts through the new orchestration path, preventing end-to-end verification of the first KMP milestone.

### Scope

- In scope: Android packaging stage integration, artifact reporting, smoke test update.
- Out of scope: Desktop, Wasm, and iOS packaging.

### Implementation Tasks

- [ ] Wire Android packaging stage into KMP orchestrator.
- [ ] Persist APK artifact metadata in unified build report.
- [ ] Add smoke test for scaffold plus Android package flow.

### Deliverables

- Android packaging path callable from KMP build manager.
- Build report with APK output path and stage duration.

### Definition of Done

- [ ] Generated KMP sample packages a debug APK successfully.
- [ ] Build report includes Android target status, duration, and artifact path.
- [ ] Smoke test passes in CI for KMP scaffold plus package flow.

### Acceptance Tests

- [ ] Unit tests for Android stage integration points.
- [ ] Smoke/integration test for full KMP scaffold to APK path.
- [ ] Manual verification on representative Android host profile.

### Telemetry and Diagnostics

- Metrics/events: android_package_start, android_package_finish, android_package_error.
- Error categories: packaging_error, dex_error, manifest_error.
- Required logs: stage transitions and final APK artifact location.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: disable flag and route builds to Android-only pipeline.

### Dependencies

- Blocked by: KMP-201.
- Blocks: KMP-206.

### Risks and Mitigations

- Risk: packaging instability on low-memory devices.
- Mitigation: memory guardrails, clear failure diagnostics, and serialized heavy stages.

### Verification Notes

Attach APK path, build logs, and smoke test evidence.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.
