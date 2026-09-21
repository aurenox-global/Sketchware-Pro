# Sketchware Pro Improvement Backlog

Execution backlog derived from sketchware_pro_improvement_plan.md.

## Scope

This file breaks the full v2.0 plan into actionable engineering tasks for incremental delivery.

## Phase 0 - Foundations (Weeks 1-4)

- [ ] Migrate core app modules to Kotlin-first interoperability strategy.
- [x] Introduce feature flags for progressive rollouts.
- [x] Define module boundaries and ownership map.
- [x] Establish CI baseline (compile + lint + smoke tests).
- [x] Add initial architectural fitness checks (forbidden dependencies between layers).

## Phase 1 - Build System (Weeks 5-12)

- [x] Add incremental compilation dependency graph for Java units.
- [x] Add Kotlin compilation pipeline with KSP-ready extension points.
- [x] Integrate Gradle Tooling API bridge in isolated process.
- [x] Add build telemetry (cold build, incremental build, cache hit rate).
- [x] Add dependency sync and lock snapshot.

## Phase 2 - Code Editor and LSP (Weeks 13-20)

- [x] Add LSP client abstraction layer and diagnostics stream.
- [x] Add completion providers with timeout and fallback strategy.
- [x] Add go-to-definition and references navigation contract.
- [x] Add real-time diagnostics rendering in editor gutter.
- [x] Add editor performance instrumentation (P50/P95 latency).

## Phase 3 - Debug and Profiling (Weeks 21-28)

- [x] Add JDWP bridge skeleton and thread-safe session manager.
- [x] Add breakpoint management domain model.
- [x] Add variable inspector transport and serializer.
- [x] Add profiler event model (cpu/memory/network).
- [x] Add crash symbolication workflow contract.

## Phase 4 - AI and Blocks (Weeks 29-38)

- [x] Add project semantic index abstraction for retrieval-augmented prompts.
- [x] Add typed block system core primitives.
- [x] Add block type checker and mismatch diagnostics.
- [x] Add source-to-block conversion MVP parser.
- [x] Add block-to-source deterministic printer tests.

## Phase 5 - Plugins and Security (Weeks 39-46)

- [x] Add plugin manifest schema and signature validation hooks.
- [x] Add isolated plugin classloader lifecycle manager.
- [x] Add static analysis pipeline orchestration.
- [x] Add security score model and report format.
- [x] Add dependency vulnerability scan adapter.

## Phase 6 - UX and Performance (Weeks 47-52)

- [x] Add adaptive layout policy for compact/medium/expanded widths.
- [x] Add command palette data model and provider registry.
- [x] Add startup profiling and regression thresholds.
- [x] Add accessibility checks and issue reporting.
- [x] Finalize KPI dashboard and release gates.

## Phase 7 - Kotlin Multiplatform Track (Proposed)

- [x] Introduce KMP project model and source set hierarchy contracts.
- [x] Add KMP Gradle script builder with deterministic scaffold templates.
- [x] Add KMP version-catalog generation.
- [x] Add multi-target build orchestrator foundation for Android/Desktop with Wasm/iOS stubs.
- [x] Add platform-aware blocks and automatic expect/actual generation (block scope metadata and realtime editor compatibility warnings are active; expect/actual generator now includes starter template catalog hooks (logger, key-value, clock), and generated sample scaffold consumes those bindings across common/Android/Desktop smoke flows).
- [x] Add KMP dependency compatibility resolver and migration suggestions (target-aware dependency compatibility resolver is active with deterministic fixture matrix and unsupported-target diagnostics, KMP alternative suggestion catalog/action hook covers Retrofit, Room, Gson, and RxJava, migration analyzer reports migrable ratio/manual effort/risk summary, and migration assistant wizard flow persists report output).
- [x] Add multi-target preview and compatibility diagnostics panel (KMP target selector, simulated multi-target preview panel, and compatibility inspector with aggregated warnings/quick-fix actions are active in Logic Editor).
- [ ] Add migration assistant from Android-only projects to KMP projects.

## Phase 8 - Android Studio Leadership Track (Proposed)

- [ ] Connect real LSP backend (replace NoOpLspClient path) and enable by default with safe fallback.
- [ ] Implement production JDWP bridge with breakpoints, variables, thread inspection, and usable profiler sessions.
- [ ] Raise app targetSdk to modern baseline and close compatibility regressions across supported Android versions.
- [ ] Complete plugin security chain with real signature verification backend and CVE vulnerability feed.
- [ ] Add instrumented tests and stronger CI quality gates for UI/device validation.
- [ ] Complete Android-only to KMP migration assistant and end-to-end iOS assisted workflow.
- [ ] Track this phase in docs/android_studio_leadership_guide.md.

## Upcoming Milestones (Planned)

Milestone: M1 - KMP Foundations (Weeks 1-8)

- [x] Week 1-2: Implement `KmpProject` model, target metadata, and source set hierarchy contracts.
- [x] Week 3-4: Complete KMP Gradle script generation with deterministic templates and version catalog entries.
- [x] Week 5-6: Wire multi-target build orchestration foundation for Android and Desktop first, with Wasm/iOS stubs.
- [x] Week 7-8: Add build report aggregation, smoke tests, and feature-flagged IDE entry points (`ProjectBuilder` persists `kmp_multi_target_build_report.json`, `BuildSettings` gates experimental bridge/timeouts, and CI runs `check_kmp_smoke_workflow.sh`).

Acceptance Criteria (M1)

- [x] New KMP project scaffold generation produces valid `shared`, `androidApp`, and `desktopApp` module structure.
- [x] Single shared source set compiles and packages both Android debug APK and Desktop runnable JAR from one project.
- [x] Build orchestration emits per-target status, duration, and artifact path in a unified report.
- [x] KMP functionality is fully behind a feature flag and does not regress Android-only project creation/build.
- [x] CI runs at least one KMP smoke workflow (scaffold + compile + package for Android and Desktop).

## Active Milestone (Current)

Milestone: M0 - Rollout Infrastructure

- [x] Feature flag storage and defaults bootstrap.
- [x] Feature flag wired to Code Viewer edit and save actions.
- [x] Feature flag developer UI for toggling experimental features.
- [x] Persist and surface build performance metrics.

## Notes

- Deliver features behind flags first, then graduate to default-on after stability targets.
- Keep each work item independently releasable.
- Track performance regressions on every merged change set.
- CI baseline now uses `app/lint-baseline.xml`, so lint is blocking for new issues while legacy findings remain tracked in baseline.
- Architectural fitness checks currently enforce boundaries for `pro.sketchware.featureflags`, `pro.sketchware.metrics`, and `pro.sketchware.ai` (no UI dependency from core; no Activities dependency from AI core).
- Build telemetry foundation is active: total duration, last-build stage breakdown, cold/incremental classification, and estimated cache hit rate are persisted and visible (real cache hit signal is still pending).
- Dependency sync lock is active via `:app:writeDependencySnapshot` and `:app:verifyDependencySnapshot`, with versioned lock file at `docs/dependency-snapshot.lock` and CI enforcement in `verification.yml`.
- Incremental Java graph foundation is active in `mod.hey.studios.compiler.incremental` and currently runs as non-blocking analysis/logging during Java compilation.
- Kotlin compiler now exposes pipeline extension points (`KotlinCompilationPipeline`) with a KSP-ready placeholder extension and Java bridge registration methods.
- Isolated Gradle tooling bridge foundation is active in `mod.hey.studios.compiler.tooling.GradleToolingBridge`, wired as optional non-blocking dependency sync probe (`enable_gradle_tooling_bridge`, timeout key `gradle_tooling_timeout_ms`).
- LSP foundation is active in `pro.sketchware.lsp` with client/session contracts, diagnostics stream snapshot model, and no-op client wiring in editor activities behind feature flag `EDITOR_LSP_ABSTRACTION`.
- LSP completion foundation now includes timeout + fallback orchestration (`LspCompletionCoordinator`) with primary provider execution cap and keyword fallback (`NoOpCompletionProvider` -> `KeywordCompletionProvider`) via `LspDocumentSession.requestCompletions(...)`.
- LSP navigation contract now includes definition/references requests (`requestDefinition`, `requestReferences`) backed by timeout + fallback orchestration (`LspNavigationCoordinator`) and local symbol fallback provider (`LocalSymbolNavigationProvider`).
- Real-time diagnostics are now rendered in editor gutter via Sora `DiagnosticsContainer` (`EditorDiagnosticsGutterRenderer`), fed by live LSP updates on content changes and local diagnostics analyzer fallback (`LocalDiagnosticsAnalyzer`).
- Editor performance instrumentation is active with persisted latency samples and percentile summaries (P50/P95) for completion, definition, references, diagnostics analyze/render, and publish-to-render lag (`EditorPerformanceMetricsStore`) surfaced in developer feature flags.
- JDWP debug foundation is active under `pro.sketchware.debugger.jdwp` with session contracts, no-op bridge, and a thread-safe session manager (`JdwpDebugSessionManager`) supporting create/start/stop/remove/snapshot and exclusive active-session switching.
- Breakpoint management domain model is active under `pro.sketchware.debugger.breakpoints` with typed breakpoints (`LINE`, `METHOD`, `EXCEPTION`), optional conditions/hit count, and a thread-safe registry (`JdwpBreakpointManager`) integrated into `JdwpDebugSessionManager` lifecycle cleanup.
- Variable inspector foundation is active under `pro.sketchware.debugger.variables` with transport contract (`JdwpVariableInspectorTransport` + no-op transport) and JSON serialization (`JsonJdwpVariableSerializer`) for request/response snapshots of debugger variables.
- Profiler event model is active under `pro.sketchware.debugger.profiler` with typed CPU/memory/network events and session-scoped ring buffer (`JdwpProfilerEventBuffer`) integrated into `JdwpDebugSessionManager` cleanup lifecycle.
- Crash symbolication workflow contract is active under `pro.sketchware.debugger.symbolication` with request/result/frame/status models and no-op workflow backend (`NoOpCrashSymbolicationWorkflow`) wired into `JdwpDebugSessionManager` lifecycle (`crashSymbolication()`, `clearSession`, `clear`).
- Local AI now has a project semantic index abstraction in `pro.sketchware.ai.rag` (`LocalAiProjectSemanticIndex`) with in-memory implementation and project file indexer, and `SrcCodeEditor` injects retrieved context into `LocalAiPromptFactory` prompts for retrieval-augmented generation.
- Typed block system core primitives are now available under `pro.sketchware.blocks.typing` (`TypedBlockValueType`, `TypedBlockKind`, `TypedBlockInputPort`, `TypedBlockSpecParser`, `TypedBlockSignature`, `TypedBlockRegistry`) and legacy `BlockBean` can be bridged via `toTypedSignature()`.
- Block type checking is now available under `pro.sketchware.blocks.typing` via `TypedBlockTypeChecker` + typed diagnostics/result models (`TypedBlockTypeDiagnostic*`, `TypedBlockTypeCheckResult`) with mismatch detection for connected input references and invalid block references.
- Source-to-block conversion MVP parser is active under `pro.sketchware.blocks.generator.components.parsers` (`SourceToBlockMvpParser`) with parse result/status/issue models and JavaParser-backed conversion into linked `BlockBean` graphs (`nextBlock`, `subStack1`, `subStack2`) for core control-flow statements.
- Block-to-source deterministic coverage is active via `BlockToSourceMvpPrinter` and JVM unit tests (`BlockToSourceMvpPrinterTest`) that verify stable output across repeated runs and shuffled block ordering.
- Plugin manifest schema + signature validation hooks are active under `pro.sketchware.plugins.manifest` and `pro.sketchware.plugins.security` with strict JSON schema parsing (`PluginManifestParser`), typed manifest/issues/result models, and pluggable signature verification (`PluginSignatureValidator`, `NoOpPluginSignatureValidator`, `PluginManifestVerifier`).
- Isolated plugin classloader lifecycle manager is active under `pro.sketchware.plugins.runtime` with session/config/factory abstractions (`PluginClassLoaderLifecycleManager`, `PluginClassLoaderSession`, `PluginClassLoaderFactory`) plus URL-based isolated loader support and JVM lifecycle tests (`PluginClassLoaderLifecycleManagerTest`).
- Static analysis pipeline orchestration is active under `pro.sketchware.plugins.security.analysis` with pluggable analyzers, aggregated findings/report models, default analyzer chain (`PluginManifestStaticAnalyzer`, `PluginRuntimeConfigStaticAnalyzer`), and JVM orchestration tests (`PluginStaticAnalysisPipelineOrchestratorTest`).
- Security score model and report format are active under `pro.sketchware.plugins.security.scoring` with weighted scoring (`PluginSecurityScoreCalculator`), risk levels/breakdown models, and deterministic JSON report formatter (`JsonPluginSecurityScoreReportFormatter`) covered by JVM tests.
- Dependency vulnerability scan adapter is active under `pro.sketchware.plugins.security.vulnerability` with adapter/result/contracts (`PluginDependencyVulnerabilityScanAdapter`, `PluginDependencyVulnerabilityScanResult`) and integrated analyzer (`PluginDependencyVulnerabilityStaticAnalyzer`) wired into the default static analysis pipeline.
- Adaptive layout policy is active under `pro.sketchware.ui.layout` (`AdaptiveLayoutPolicy`, `AdaptiveLayoutSnapshot`, `AdaptiveLayoutWidthClass`) with compact/medium/expanded width classes, deterministic thresholds, and runtime integration in `BaseAppCompatActivity` + `FeatureFlagsActivity` for responsive spacing and size-class visibility.
- Command palette foundation is active under `pro.sketchware.commandpalette` with typed command/query/result models (`CommandPaletteAction`, `CommandPaletteQuery`, `CommandPaletteSearchResult`), pluggable provider contract (`CommandPaletteProvider`), and deterministic thread-safe registry orchestration (`CommandPaletteProviderRegistry`) including provider-level reports and failure containment.
- Startup profiling foundation is active under `pro.sketchware.metrics` with startup checkpoint tracker (`StartupPerformanceTracker`), persisted metrics + regression reporting (`StartupPerformanceMetricsStore`), and dynamic threshold policy (`StartupRegressionPolicy`) surfaced in developer feature flags.
- Accessibility checks/reporting foundation is active under `pro.sketchware.accessibility` and `pro.sketchware.metrics` with typed node snapshots + issue models, deterministic checker engine (`AccessibilityChecksEngine`), runtime view snapshot reporting (`AccessibilityRuntimeReporter`), and persisted accessibility issue metrics (`AccessibilityIssueReportStore`) surfaced in developer feature flags.
- KPI dashboard and release-gate foundation is active under `pro.sketchware.metrics` with cross-domain snapshot aggregation (`KpiDashboardStore`), release gate evaluation (`KpiDashboardReleaseGatesEvaluator`), and developer-facing summaries/resets in feature flags.
- CI enforcement now includes KPI release-gate checks via `scripts/check_kpi_release_gates.sh` (required suites + aggregate test floor + zero failures/errors) executed in `.github/workflows/verification.yml`.
- Module boundaries and ownership map documented in `docs/module_boundaries_ownership_map.md`, aligned with current CI architecture fitness checks.
- Kotlin-first interoperability kickoff delivered in `pro.sketchware.metrics`: `KpiDashboardGateInputs` and `KpiDashboardReleaseGatesResult` migrated to Kotlin with Java-compatible API (`@JvmField` fields and `@JvmStatic` factory) and validation via unit tests/lint/check scripts.
- Kotlin-first interoperability expanded in `pro.sketchware.metrics`: `KpiDashboardReleaseGate`, `KpiDashboardReleaseGateStatus`, and `StartupRegressionDecision` migrated to Kotlin while preserving Java call sites and behavior.
- Kotlin-first interoperability expanded (phase 3) in `pro.sketchware.metrics`: `StartupRegressionPolicy` and `KpiDashboardReleaseGatesEvaluator` migrated to Kotlin objects with Java static interop (`@JvmStatic`) and existing thresholds/decision behavior preserved by tests.
- Kotlin-first interoperability expanded (phase 4) in `pro.sketchware.metrics`: `KpiDashboardSnapshot` and `KpiDashboardStore` migrated to Kotlin with Java-compatible field/static access (`@JvmField`/`@JvmStatic`) and no regression in KPI summary or reset flows.
- Kotlin-first interoperability expanded (phase 5) in `pro.sketchware.metrics`: `StartupPerformanceTracker` migrated to Kotlin object with Java static interop (`@JvmStatic`) preserving one-shot startup capture semantics and feature-flag gating.
- Kotlin-first interoperability expanded (phase 6) in `pro.sketchware.metrics`: `AccessibilityIssueReportStore` migrated to Kotlin object including `Snapshot` model with Java-compatible static/field access and preserved aggregation/formatting behavior.
- Kotlin-first interoperability expanded (phase 7) in `pro.sketchware.metrics`: `EditorPerformanceMetricsStore` migrated to Kotlin object with Java-compatible constants, static APIs, and snapshot/operation stats models while preserving percentile and sample-window behavior.
- Kotlin-first interoperability expanded (phase 8) in `pro.sketchware.metrics`: `StartupPerformanceMetricsStore` migrated to Kotlin object with Java-compatible static APIs and nested stats/snapshot models while preserving regression threshold reporting behavior.
- Kotlin-first interoperability expanded (phase 9) in `pro.sketchware.metrics`: `BuildMetricsStore` migrated to Kotlin object with Java-compatible overloads and nested stage/snapshot models while preserving incremental/cold profiling and cache-hit estimate calculations.
- Kotlin Multiplatform functional planning is documented in `docs/kmp_functional_plan.md`, including architecture, target matrix, expect/actual strategy, migration flow, and phased rollout.
- Kotlin Multiplatform issue breakdown is tracked in `docs/kmp_phase7_epic_issue_checklist.md` (epics, stories, dependencies, and definition of done).
- Kotlin Multiplatform sprint planning is tracked in `docs/kmp_phase7_sprint_board.md` (Sprint 1 and Sprint 2 goals, capacity, and exit evidence).
- Kotlin Multiplatform issue authoring template is documented in `docs/kmp_story_issue_template.md`.
- Kotlin Multiplatform Sprint 1 ready-to-copy issue drafts are tracked in `docs/kmp_s1_issue_batch.md`.
- Kotlin Multiplatform Sprint 2 ready-to-copy issue drafts are tracked in `docs/kmp_s2_issue_batch.md`.
- Kotlin Multiplatform GitHub import workflow is documented in `docs/kmp_github_issue_import.md` and automated by `scripts/create_kmp_phase7_github_issues.sh`.
- Kotlin Multiplatform GitLab import workflow is documented in `docs/kmp_gitlab_issue_import.md` and automated by `scripts/create_kmp_phase7_gitlab_issues.sh`.
- KMP implementation has started under `pro.sketchware.kmp` with foundation model/targets/default hierarchy, source-set hierarchy validator, JSON serializer/parser, new feature flag `KMP_EXPERIMENTAL_ENABLE`, and passing unit tests (`KmpProjectSerializerTest`, `KmpSourceSetHierarchyValidatorTest`).
- KMP project creation now includes initial scaffold generation behind `KMP_EXPERIMENTAL_ENABLE` via `KmpGradleScriptBuilder` and `KmpScaffoldInitializer`, producing `shared`, `androidApp`, and `desktopApp` modules and writing KMP metadata to `files/kmp/project.json` for new projects.
- Build/export pipelines now detect and validate `files/kmp/project.json` via `ProjectBuilder`, logging diagnostics and running Android compatibility mode when KMP metadata is present or invalid.
- Build mode resolution is now explicit via `KmpBuildPipelineResolver` (`ANDROID_COMPATIBILITY` vs `KMP_GRADLE_EXPERIMENTAL`), and `ProjectBuilder` can trigger optional KMP Gradle dependency sync (`:shared:dependencies`) when `enable_kmp_gradle_bridge` is enabled and a KMP Gradle wrapper exists.
- KMP scaffold now generates wrapper scripts/properties (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`) and copies `gradle-wrapper.jar` from app assets during project creation; Build Settings now exposes `enable_kmp_gradle_bridge` for toggling the experimental KMP bridge from UI.
- Build Settings now also exposes `kmp_gradle_timeout_ms`, and `ProjectBuilder` writes KMP bridge run diagnostics to `bin/kmp_gradle_bridge_sync.log` (mode, reason, attempted/successful/timedOut, duration, exit code, details).
- Multi-target orchestration foundation is active under `pro.sketchware.kmp` (`KmpMultiTargetBuildOrchestrator`, `KmpGradleTargetBuildRunner`) with Android/Desktop execution, Wasm/iOS stubs, and unified per-target report models.
- CI verification now includes `scripts/check_kmp_smoke_workflow.sh`, which scaffolds a temporary KMP project and runs `:androidApp:assembleDebug` + `:desktopApp:desktopJar` with APK/JAR artifact checks.
- M1 executive status: Week 1-2 through Week 7-8 are completed for KMP foundations (project model, deterministic templates, wrapper scaffold, version catalog, multi-target orchestration/reporting, feature-flagged bridge entry points, and CI smoke packaging validation for Android/Desktop).
- EPIC-KMP-03 kickoff is active: `KmpExpectActualGenerator` now generates expect/actual files from platform-aware contracts (including missing-implementation TODO fallback) and scaffold/smoke flows compile generated outputs for Android/Desktop.
- KMP block scope metadata model is active under `pro.sketchware.blocks.typing` (`TypedBlockScope` + `TypedBlockSignature.scope`) with legacy inference migration via `TypedBlockSignature.fromLegacy(...)`, preserving backward compatibility while classifying scopes such as `COMMON`, `ANDROID_ONLY`, and `DESKTOP_ONLY`.
- KMP target compatibility validator is active end-to-end: `TypedBlockTargetCompatibilityValidator` emits `TARGET_SCOPE_INCOMPATIBLE` diagnostics with migration suggestions, `TypedBlockTypeChecker.check(blocks, enabledTargets)` appends scope diagnostics, `KmpBlockCompatibilityValidator.validate(...)` bridges KMP targets, and `LogicEditorActivity.q()` now runs realtime checks with live warning subtitle/toast updates.
- KMP starter template catalog is active in `KmpExpectActualGenerator`: `starterContracts()` seeds logger/key-value/clock templates, `starterContracts(overrides)` supports per-contract implementation overrides, and scaffold/smoke sample code now consumes `logInfo`, `putKeyValue`, `getKeyValue`, and `currentTimeMillis` from `GeneratedPlatformBindings`.
- KMP dependency compatibility resolver is active in `KmpDependencyCompatibilityResolver` with deterministic fixture matrix (Android-only, JVM-only, and multiplatform libraries), per-target support map, unsupported-target diagnostics, and report serialization via `KmpDependencyCompatibilityReportSerializer` for migration pipeline consumption.
- KMP alternative suggestion catalog is active in `KmpDependencyAlternativeSuggestionCatalog`: Android-only migration hints for Retrofit, Room, Gson, and RxJava are mapped to KMP alternatives, and resolver diagnostics now expose `open_kmp_dependency_alternatives` as a UI action hook when mappings are available.
- KMP migration analyzer is active in `KmpMigrationAnalyzer` with summary outputs (migrable ratio, estimated manual work hours, risk level/summary) plus serialized report output via `KmpMigrationAnalysisReportSerializer` for migration assistant pipeline ingestion.
- KMP migration assistant workflow is active in `KmpMigrationAssistantWorkflow`: the 5-step flow (analysis, targets, dependencies, restructure, verify) now completes with persisted `kmp_migration_report.json` output for legacy project migration runs.
- KMP target selector is active in `LogicEditorActivity`: menu action `menu_logic_kmp_target` lets users switch target context (persisted in `ProjectSettings.SETTING_KMP_EDITOR_TARGET`), scope warnings now validate against the selected target via `KmpBlockCompatibilityValidator.validate(..., selectedTarget)`, and Show Source propagates `kmp_target_id` as codegen context metadata.
- KMP multi-target preview panel is active in `LogicEditorActivity`: menu action `menu_logic_kmp_preview` opens simulated containers for Android/Desktop/Web/iOS, and Android/Desktop previews render a shared snapshot generated by `KmpPreviewSnapshotBuilder` from current event/source/block context.
- KMP compatibility inspector panel is active in `LogicEditorActivity`: menu action `menu_logic_kmp_inspector` aggregates selected-target scope warnings using `KmpCompatibilityInspectorFormatter` and exposes quick-fix actions to switch target context or open multi-target preview.
- KMP build performance telemetry is active in `KmpBuildPerformanceMetricsStore`: `ProjectBuilder` records orchestrator reports as cold/incremental runs with per-target stage durations, `FeatureFlagsActivity` now surfaces KMP summary/trend/stage metrics, and KPI dashboard summary includes KMP run counts for release monitoring.
- KMP error taxonomy and user-facing diagnostics are active: `KmpBuildFailureTaxonomy` classifies top failure patterns into categories with remediation hints, `ProjectBuilder` persists `kmp_multi_target_build_diagnostics.json`, and build logs now print actionable KMP remediation messages when orchestration fails.
- KMP developer docs and sample app are published: `docs/kmp_developer_docs.md` documents workflow/diagnostics, `docs/kmp_sample_app` provides a versioned shared Android/Desktop sample, and Feature Flags now includes `KMP Developer Docs` as an in-app entry with sample build instructions.
- KMP smoke CI gate is published in `.github/workflows/kmp-smoke.yml`: pull requests that touch KMP paths now run dedicated KMP unit tests plus `scripts/check_kmp_smoke_workflow.sh` (Android/Desktop scaffold compile/package checks) as a merge-quality signal.
- Phase 8 leadership kickoff is active with Q1 baseline setup (androidTest dependency/runtime wiring and dedicated instrumented CI gate), tracked in `docs/android_studio_leadership_guide.md`.
- Phase 8 L1 progress: LSP default flag is now enabled for new users and `LspClientFactory` follows a real-first strategy with safe runtime fallback to `NoOpLspClient`.
- Phase 8 A1 progress: app module `targetSdk` is raised to 35 as modernization baseline while compatibility closure continues.
- Phase 8 D1 progress: `JdwpDebugSessionManager` now resolves bridge/variable inspector/symbolication through a real-first runtime factory (`JdwpRuntimeFactory`) and only falls back to NoOp when no concrete runtime implementation is available.
- Phase 8 Q1 progress: android instrumented smoke suite now includes launcher intent resolution and `MainActivity` launch validation using `ActivityScenario`.
- Phase 8 D1 progress: runtime crash symbolication backend is active via `RealCrashSymbolicationWorkflow` with stacktrace frame parsing, persisted request/session results, and unit coverage for success/failure flows.
- Phase 8 Q1 progress: smoke suite now validates `MainActivity` recreation stability and core view availability (`drawer_layout`, `bottom_nav`) after lifecycle restart.
- Phase 8 D1 progress: profiler now follows real-first runtime resolution via `JdwpRuntimeFactory` and `RealJdwpProfilerEventBuffer`, exposing on-demand CPU/Mem/Network sampling for attached sessions in `JdwpDebugSessionManager.recordProfilerSample(...)`.
- Phase 8 Q1 progress: smoke suite now validates bottom navigation stability after lifecycle bounce and asserts core tabs (`item_projects`, `item_sketchub`) are present.
- Phase 8 D1 progress: profiler session report export is now available through `JdwpDebugSessionManager.buildProfilerReportJson(...)` and `exportProfilerReport(...)`, with JSON serializer coverage (`JsonJdwpProfilerReportSerializerTest`).
- Phase 8 Q1 progress: CI instrumented workflow now enforces anti-flaky quality gates via `scripts/check_android_instrumented_gates.sh` (required smoke suite, minimum test floor, zero failures/errors).
