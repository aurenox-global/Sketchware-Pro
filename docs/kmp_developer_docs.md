# KMP Developer Docs

This guide documents the Kotlin Multiplatform experimental flow used in Sketchware Pro and includes a versioned sample project.

## Scope

- Feature flag: `KMP_EXPERIMENTAL_ENABLE`
- Build orchestration report: `bin/kmp_multi_target_build_report.json`
- Diagnostics taxonomy report: `bin/kmp_multi_target_build_diagnostics.json`
- Metrics dashboard: Feature Flags -> KMP Build Metrics

## Sample Project

- Path: `docs/kmp_sample_app`
- Modules: `shared`, `androidApp`, `desktopApp`
- Shared logic entry points:
  - `shared/src/commonMain/kotlin/pro/sketchware/kmpsample/Platform.kt`
  - `shared/src/commonMain/kotlin/pro/sketchware/kmpsample/GeneratedPlatformBindings.kt`

## Build The Sample

Run from repository root:

```bash
./gradlew -p docs/kmp_sample_app --no-daemon :androidApp:assembleDebug :desktopApp:desktopJar
```

Expected artifacts:

- Android: `docs/kmp_sample_app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- Desktop: `docs/kmp_sample_app/desktopApp/build/libs/*desktop*.jar`

## Diagnostics Reference

The KMP taxonomy currently maps common failures to actionable remediation hints, including:

- Gradle wrapper missing
- Build timeout
- Java toolchain mismatch
- Android SDK path or license issues
- Dependency resolution and duplicate class conflicts
- Kotlin unresolved references and compilation symbol failures
- Plugin configuration incompatibility

If taxonomy cannot classify a failure, fallback code `KMP_ERR_UNKNOWN` is used with generic remediation guidance.

## In-App Entry

Open Feature Flags and use the preference named `KMP Developer Docs` to view this workflow summary directly in-app.
