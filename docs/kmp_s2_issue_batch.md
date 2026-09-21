# KMP Sprint 2 Issue Batch

Ready-to-copy issue drafts for Sprint 2 stories:
- KMP-203
- KMP-206
- KMP-301
- KMP-302
- KMP-303
- KMP-304

Optional stretch issue:
- KMP-401

## KMP-203: Integrate Desktop target packaging path

### Summary

- Story ID: KMP-203
- Epic: EPIC-KMP-02
- Priority: p0
- Estimate: 5 points

### Problem

Generated KMP projects still lack a production-ready Desktop packaging path through the orchestrator, so Android plus Desktop parity is incomplete for the first functional milestone.

### Scope

- In scope: Desktop compile and runnable JAR packaging path, artifact registration, smoke checks.
- Out of scope: native installers and platform-specific launcher packaging.

### Implementation Tasks

- [ ] Wire Desktop packaging stage into KMP orchestrator.
- [ ] Add Desktop artifact metadata to unified build report.
- [ ] Add smoke checks for scaffold plus Desktop package path.

### Deliverables

- Desktop packaging path callable from KMP build manager.
- Build report includes Desktop status, duration, and artifact path.

### Definition of Done

- [ ] Generated KMP sample creates a runnable Desktop JAR.
- [ ] Build report contains Desktop artifact path and stage duration.
- [ ] Smoke checks pass for Desktop package flow.

### Acceptance Tests

- [ ] Unit tests for Desktop packaging stage integration.
- [ ] Smoke test for scaffold-to-desktop-artifact pipeline.
- [ ] Manual run of produced Desktop JAR on JVM host.

### Telemetry and Diagnostics

- Metrics/events: desktop_package_start, desktop_package_finish, desktop_package_error.
- Error categories: desktop_compile_error, jar_packaging_error, classpath_error.
- Required logs: stage transitions, main class, output artifact location.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: disable KMP path and keep Android-only and legacy Desktop paths.

### Dependencies

- Blocked by: KMP-201.
- Blocks: KMP-206.

### Risks and Mitigations

- Risk: classpath resolution differences across hosts.
- Mitigation: deterministic classpath assembly and fixture-based packaging tests.

### Verification Notes

Attach build logs, Desktop artifact path, and JVM run output.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.

## KMP-206: Unified build report and artifact index

### Summary

- Story ID: KMP-206
- Epic: EPIC-KMP-02
- Priority: p0
- Estimate: 3 points

### Problem

KMP build output is fragmented by stage and target, which makes diagnostics and milestone validation difficult. A single report contract is needed for visibility and CI traceability.

### Scope

- In scope: unified report schema, per-target status and duration, artifact index entries.
- Out of scope: long-term analytics dashboard integration.

### Implementation Tasks

- [ ] Define unified KMP build report schema.
- [ ] Persist per-target status, duration, artifact path, and error summary.
- [ ] Expose report to diagnostics and CI artifacts.
- [ ] Add tests for schema consistency and target coverage.

### Deliverables

- Build report object and persistence contract.
- Artifact index containing Android and Desktop outputs.

### Definition of Done

- [ ] Each enabled target emits status, duration, and artifact path when available.
- [ ] Report is persisted and retrievable from diagnostics surface.
- [ ] Failed targets include explicit error category and summary.

### Acceptance Tests

- [ ] Unit tests for report schema and merging behavior.
- [ ] Integration test with mixed target outcomes.
- [ ] Manual verification in diagnostics UI or exported log artifact.

### Telemetry and Diagnostics

- Metrics/events: kmp_report_persist_ok, kmp_report_persist_error.
- Error categories: report_schema_error, report_persist_error.
- Required logs: target list, report path, report version.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: fallback to stage-local logs while report path is disabled.

### Dependencies

- Blocked by: KMP-202, KMP-203.
- Blocks: S2 exit validation and stabilization stories.

### Risks and Mitigations

- Risk: schema drift between orchestrator and diagnostics layers.
- Mitigation: shared schema module and compatibility tests.

### Verification Notes

Attach one successful and one partial-failure report sample.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.

## KMP-301: Add block scope metadata model

### Summary

- Story ID: KMP-301
- Epic: EPIC-KMP-03
- Priority: p0
- Estimate: 3 points

### Problem

Existing block metadata is not target-aware. Without scope metadata, the editor cannot safely distinguish common blocks from platform-specific blocks in KMP projects.

### Scope

- In scope: block scope enum, metadata schema update, migration path for legacy blocks.
- Out of scope: full compatibility UI and diagnostics.

### Implementation Tasks

- [ ] Define block scope metadata contract.
- [ ] Add migration for existing block definitions.
- [ ] Add parser and serializer updates for scope field.
- [ ] Add tests for legacy migration and new scope assignment.

### Deliverables

- Scope-aware block metadata model.
- Migration path preserving legacy block behavior.

### Definition of Done

- [ ] Legacy blocks are mapped to compatible default scopes.
- [ ] New blocks persist declared scope reliably.
- [ ] Scope metadata survives load-save round trips.

### Acceptance Tests

- [ ] Unit tests for metadata migration and serialization.
- [ ] Regression test for existing non-KMP projects.
- [ ] Manual verification by opening legacy project and inspecting block metadata.

### Telemetry and Diagnostics

- Metrics/events: block_scope_migration_ok, block_scope_migration_error.
- Error categories: block_scope_parse_error, block_scope_migration_error.
- Required logs: migrated block counts and fallback scope usage.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: ignore new scope field and use legacy compatibility mapping.

### Dependencies

- Blocked by: none.
- Blocks: KMP-302, KMP-303.

### Risks and Mitigations

- Risk: accidental behavior changes in legacy block sets.
- Mitigation: default-safe mapping plus regression fixtures from existing projects.

### Verification Notes

Attach migration summary and round-trip fixture results.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.

## KMP-302: Implement target compatibility validator in editor

### Summary

- Story ID: KMP-302
- Epic: EPIC-KMP-03
- Priority: p0
- Estimate: 3 points

### Problem

KMP projects need immediate feedback when block usage conflicts with enabled targets. Without validation, users discover incompatibilities late during code generation or build.

### Scope

- In scope: validator logic, diagnostics model, editor-side warning rendering.
- Out of scope: automated code fixes for all incompatibilities.

### Implementation Tasks

- [ ] Add compatibility validator using block scope metadata and active targets.
- [ ] Add diagnostics payload with severity and suggested action.
- [ ] Surface warnings in editor context where block is placed.
- [ ] Add tests for valid and invalid target combinations.

### Deliverables

- Real-time compatibility validation component.
- Editor diagnostics for incompatible block and target combinations.

### Definition of Done

- [ ] Incompatible block-target combinations are flagged during editing.
- [ ] Diagnostics include affected targets and actionable suggestion.
- [ ] Compatible combinations generate no false positives in baseline fixtures.

### Acceptance Tests

- [ ] Unit tests for validator rules.
- [ ] UI-level test or snapshot for diagnostic rendering.
- [ ] Manual verification with at least three mismatch cases.

### Telemetry and Diagnostics

- Metrics/events: kmp_compat_check_ok, kmp_compat_check_error, kmp_compat_warning_emit.
- Error categories: validator_rule_error, diagnostics_render_error.
- Required logs: block id, scope, active targets, diagnostic code.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: disable live checks and keep compile-time checks only.

### Dependencies

- Blocked by: KMP-301.
- Blocks: KMP-303 and preview quality stories.

### Risks and Mitigations

- Risk: noisy warnings reduce editor usability.
- Mitigation: severity tuning and de-duplication of repeated diagnostics.

### Verification Notes

Attach screenshots or logs for warnings and rule coverage summary.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.

## KMP-303: Implement expect/actual generator from block contracts

### Summary

- Story ID: KMP-303
- Epic: EPIC-KMP-03
- Priority: p0
- Estimate: 5 points

### Problem

Platform-dependent blocks currently lack an automatic path to shared expect declarations and per-target actual implementations. This blocks scalable KMP code generation from visual logic.

### Scope

- In scope: expect declaration generation, target actual generation, fallback stubs, compilation checks.
- Out of scope: full template catalog expansion beyond starter set.

### Implementation Tasks

- [ ] Implement expect declaration generation from platform-aware block contracts.
- [ ] Implement actual generation for Android and Desktop targets.
- [ ] Add fallback stub generation for missing implementations.
- [ ] Add compile checks for generated output in smoke samples.

### Deliverables

- expect and actual code generation pipeline from block contracts.
- Smoke sample proving generated output compiles for Android and Desktop.

### Definition of Done

- [ ] expect declarations are emitted to shared source set.
- [ ] actual implementations are emitted for enabled targets.
- [ ] generated output compiles for Android and Desktop smoke sample.

### Acceptance Tests

- [ ] Unit tests for generation templates and signatures.
- [ ] Integration test compiling generated expect/actual outputs.
- [ ] Manual validation for missing-implementation fallback behavior.

### Telemetry and Diagnostics

- Metrics/events: expect_actual_generate_ok, expect_actual_generate_error.
- Error categories: expect_signature_error, actual_generation_error, missing_actual_warning.
- Required logs: function name, target list, generated file paths.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: disable generator and keep platform-only code generation.

### Dependencies

- Blocked by: KMP-301, KMP-302.
- Blocks: KMP-304 and migration stream readiness.

### Risks and Mitigations

- Risk: signature mismatch between expect and generated actual.
- Mitigation: signature normalization + compile-time generation tests.

### Verification Notes

Attach generated file list and compile logs for Android and Desktop smoke sample.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.

## KMP-304: Add initial template catalog seed (logger, key-value, clock)

### Summary

- Story ID: KMP-304
- Epic: EPIC-KMP-03
- Priority: p1
- Estimate: 1 point

### Problem

Without starter templates, expect/actual generation lacks reusable building blocks and teams duplicate boilerplate for common platform services.

### Scope

- In scope: minimal template seed with logger, key-value store, and clock contracts.
- Out of scope: full template marketplace and advanced override UI.

### Implementation Tasks

- [ ] Add template definitions for logger, key-value store, and clock.
- [ ] Wire template lookup from generation pipeline.
- [ ] Add template tests for signature correctness.

### Deliverables

- Initial expect/actual template catalog seed.
- Generator integration with template lookup.

### Definition of Done

- [ ] Three starter templates are available and test-covered.
- [ ] Generator resolves templates by contract type.
- [ ] Generated template output compiles in Android and Desktop sample.

### Acceptance Tests

- [ ] Unit tests for template schema and signature matching.
- [ ] Smoke compile test using all three templates.
- [ ] Manual verification by generating and inspecting output files.

### Telemetry and Diagnostics

- Metrics/events: template_resolve_ok, template_resolve_error.
- Error categories: template_not_found, template_signature_error.
- Required logs: template id, contract mapping, output file path.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: fallback to non-template stub generation.

### Dependencies

- Blocked by: KMP-303.
- Blocks: later migration and compatibility quality improvements.

### Risks and Mitigations

- Risk: template contracts diverge from generator expectations.
- Mitigation: contract tests tied to generator signature assertions.

### Verification Notes

Attach template test output and generated sample code snapshot.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.

## Optional Stretch: KMP-401 Add dependency compatibility resolver by target

### Summary

- Story ID: KMP-401
- Epic: EPIC-KMP-04
- Priority: p1
- Estimate: 3 points

### Problem

Dependency compatibility across targets is currently opaque, which delays migration readiness and causes avoidable build failures.

### Scope

- In scope: resolver for known dependencies and per-target compatibility results.
- Out of scope: full migration wizard flow.

### Implementation Tasks

- [ ] Add compatibility resolver contract and target matrix output.
- [ ] Add known library fixtures for deterministic results.
- [ ] Add diagnostics surface for unsupported targets.

### Deliverables

- Resolver component with target compatibility result model.
- Baseline fixture set for common Android-only and KMP-ready libraries.

### Definition of Done

- [ ] Resolver returns deterministic results for fixture dependencies.
- [ ] Unsupported targets include actionable diagnostics.
- [ ] Output is consumable by migration-assistant pipeline.

### Acceptance Tests

- [ ] Unit tests for resolver rules and fixture matrix.
- [ ] Integration test with generated compatibility report.
- [ ] Manual check with one Android-only and one KMP library.

### Telemetry and Diagnostics

- Metrics/events: dep_compat_resolve_ok, dep_compat_resolve_error.
- Error categories: dep_lookup_error, dep_compat_rule_error.
- Required logs: dependency coordinate, target list, compatibility result.

### Feature Flag and Rollout

- Feature flag name: KMP_EXPERIMENTAL_ENABLE
- Default state: OFF
- Rollback strategy: disable resolver UI and rely on compile-time feedback.

### Dependencies

- Blocked by: none.
- Blocks: KMP-402 and migration assistant quality.

### Risks and Mitigations

- Risk: stale compatibility fixtures become misleading.
- Mitigation: fixture review cadence and CI check for known coordinates.

### Verification Notes

Attach fixture matrix output and one compatibility report example.

### Documentation Updates

- [ ] Update docs/kmp_phase7_epic_issue_checklist.md if scope changed.
- [ ] Update docs/kmp_phase7_sprint_board.md if estimate changed.
- [ ] Update docs/sketchware_pro_improvement_backlog.md milestone status.
