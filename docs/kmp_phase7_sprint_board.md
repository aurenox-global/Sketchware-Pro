# KMP Phase 7 Sprint Board (S1 and S2)

Planning baseline for execution of KMP Phase 7.
Source roadmap: docs/kmp_phase7_epic_issue_checklist.md.
Ready-to-copy Sprint 1 issue drafts: docs/kmp_s1_issue_batch.md.
Ready-to-copy Sprint 2 issue drafts: docs/kmp_s2_issue_batch.md.
GitHub issue import guide: docs/kmp_github_issue_import.md.
GitLab issue import guide: docs/kmp_gitlab_issue_import.md.

## Planning Assumptions

- Sprint length: 2 weeks.
- Team capacity baseline: 24 engineering points per sprint.
- Capacity split: 70% feature delivery, 20% quality, 10% unplanned risk.
- Feature flag policy: all KMP scope remains opt-in during S1 and S2.

## Prioritization Rules

1. Protect Android-only stability first.
2. Build pipeline and scaffolding before editor UX.
3. Finish Android + Desktop end-to-end path before Wasm/iOS extensions.
4. Prefer stories with deterministic tests and low rollback cost.

## Sprint 1 (S1): Foundations and Build Baseline

Sprint Goal

- Create a usable KMP scaffold and produce Android + Desktop artifacts from shared code behind a feature flag.

Planned Capacity

- Target: 24 points.
- Reserved risk buffer: 3 points.
- Planned load: 21 points.

Selected Stories

- [ ] KMP-101 (5) Define KmpProject schema and serialization contract.
- [ ] KMP-102 (3) Implement source set hierarchy contract validation.
- [ ] KMP-103 (5) Add deterministic KMP project scaffold generator.
- [ ] KMP-104 (2) Gate KMP project creation with feature flag.
- [ ] KMP-201 (3) Add compile pipeline stages for commonMain and target fan-out.
- [ ] KMP-202 (3) Integrate Android target packaging path.

Stretch (only if no blocker)

- [ ] KMP-203 (5) Integrate Desktop target packaging path.

Definition of Done for S1

- Shared scaffold generation is deterministic and repeatable.
- Android debug APK build works for generated KMP sample.
- CommonMain compilation failure correctly blocks target fan-out.
- All new entry points are hidden when feature flag is off.
- CI includes one scaffold + Android compile smoke check.

Exit Evidence for S1

- Test report covering serialization, scaffold goldens, and hierarchy validation.
- Build log with per-stage timing for commonMain and Android target.
- Demo project created from new KMP scaffold flow.

## Sprint 2 (S2): Desktop Completion and Platform Engine Start

Sprint Goal

- Complete Android + Desktop end-to-end, start platform-aware block model and expect/actual automation.

Planned Capacity

- Target: 24 points.
- Reserved risk buffer: 4 points.
- Planned load: 20 points.

Selected Stories

- [ ] KMP-203 (5) Integrate Desktop target packaging path.
- [ ] KMP-206 (3) Unified build report and artifact index.
- [x] KMP-301 (3) Add block scope metadata model.
- [x] KMP-302 (3) Implement target compatibility validator in editor.
- [x] KMP-303 (5) Implement expect/actual generator from block contracts.
- [x] KMP-304 (1) Add initial template catalog (logger, key-value, clock) seed.

Stretch (only if no blocker)

- [x] KMP-401 (3) Add dependency compatibility resolver by target.

Definition of Done for S2

- Generated KMP sample builds Android APK and Desktop runnable JAR from same shared logic.
- Build report exposes status, duration, and artifact path per target.
- Block scope incompatibilities are surfaced with actionable diagnostics.
- expect/actual generation compiles in Android + Desktop smoke sample.

Exit Evidence for S2

- Build report snapshot persisted by diagnostics store.
- Editor validation screenshots or logs for incompatible block cases.
- Smoke CI job for scaffold + Android/Desktop package.

## Risk Register (S1-S2)

- High: Gradle orchestration complexity on mobile host.
- High: Build time and memory spikes during parallel target compilation.
- Medium: Deterministic scaffold tests across environments.
- Medium: Existing block definitions migration to scope-aware metadata.

Mitigations

- Enforce staged rollout with hard feature flag gate.
- Keep S1 focused on Android first, Desktop as stretch fallback.
- Add timing and memory telemetry early in orchestrator implementation.
- Keep migration script for legacy block metadata reversible.

## Backlog Carry-Over Candidates (Post S2)

- KMP-204 WasmJs build path.
- KMP-205 iOS klib export path.
- KMP-401 to KMP-404 dependency + migration assistant stream.
- KMP-501 to KMP-503 preview and IDE UX stream.
- KMP-601 to KMP-604 stabilization and release readiness stream.
