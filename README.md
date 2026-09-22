<p align="center">
  <img src="assets/Sketchware-Pro.png" width="220" alt="Sketchware Pro">
</p>

<h1 align="center">Sketchware Pro</h1>

<p align="center">
  <b>Build real Android apps from your phone.</b><br>
  <a href="README.es.md">🇪🇸 Leer en español</a> ·
  <a href="https://aurenox-global.github.io/Sketchware-Pro/">Website</a> ·
  <a href="https://github.com/aurenox-global/Sketchware-Pro/releases">Download</a>
</p>

<p align="center">
  <img alt="version" src="https://img.shields.io/badge/version-v7.0.5.7-008dcd">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-57beee">
  <img alt="targetSdk" src="https://img.shields.io/badge/targetSdk-35-57beee">
  <img alt="license" src="https://img.shields.io/badge/license-source--available-ffc107">
  <img alt="platform" src="https://img.shields.io/badge/platform-Android-1d7a73">
</p>

---

Sketchware Pro is an Android IDE that runs on Android itself. Drag visual blocks, write Java or Kotlin,
compile on the device and get an installable APK — no computer required.

Sketchware was an app that let you build Android apps visually, right on your phone. Development stopped
years ago. **Sketchware Pro** is a community mod that keeps it alive, fixes what was broken and adds what
the original never had.

> 🔗 **Full documentation, in English and Spanish, lives here:**
> **https://aurenox-global.github.io/Sketchware-Pro/**

## Contents

- [What it does](#what-it-does)
- [Features](#features)
- [Install](#install)
- [Build from source](#build-from-source)
- [Source code map](#source-code-map)
- [Changes in this fork](#changes-in-this-fork)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [Legal notice](#legal-notice)

## What it does

| | |
|---|---|
| **Visual layout editor** | Drag widgets, edit properties, preview, generate the XML |
| **Blocks / logic editor** | Events, conditions, loops, variables, functions, searchable block palette |
| **Code editor** | Syntax highlighting, autocomplete, file tree of the generated sources |
| **Java & Kotlin** | Kotlin compilation supported through the bundled `kotlinc` toolchain |
| **Resource editor** | Images, colors, fonts, sounds and strings without leaving the app |
| **Library manager** | Firebase, Material Components, Glide, Retrofit and many more |
| **On-device compiler** | Builds the APK on the phone, with a bundled `aapt2` per architecture |
| **Custom blocks** | Create your own blocks and share them with the community |

Everything you build is plain Android: real Java sources, real resources, real APKs that belong to you.

## Features

- **Blocks that produce real code.** Nothing is locked in a proprietary format — you can read, edit and export it.
- **A complete IDE on the device.** Layout, logic, resources, manifest, signing and compilation all run locally.
- **Project catalogue.** Start from a template, a blank project or a shared `.swb` file. Everything lives in your storage.
- **Community-driven.** Free, no subscription, no ads and no telemetry of its own.
- **Built-in debugging.** Logcat, error reports and, in recent versions, real debugging work in progress.

## Install

There is no store version — you install the APK yourself.

1. Download the right APK from the [releases page](https://github.com/aurenox-global/Sketchware-Pro/releases):
   - `app-arm64-v8a-release.apk` — almost every modern phone (recommended)
   - `app-armeabi-v7a-release.apk` — old 32-bit devices
   - `app-x86_64-release.apk` / `app-x86-release.apk` — emulators
2. Allow installation from unknown sources for your browser or file manager.
3. Open the APK and install it. The signing key is unchanged, so it installs over previous builds.
4. Optional, over ADB (also keeps your data):

```bash
adb install -r app-arm64-v8a-release.apk
```

**Requirements:** Android 8.0 (API 26) or newer. An ARM64 device is recommended.

## Build from source

Requirements:

- **JDK 17** — `java -version` should report 17.x.
- **Android SDK** — platform 36 and build-tools 35; point `local.properties` at it (`sdk.dir=/path/to/android-sdk`).
- **Google Services** — `app/google-services.json` is optional. The helper scripts create a temporary placeholder from `app/src/debug/google-services.json` when needed.

```bash
# debug build
./gradlew :app:assembleDebug

# signed release build
./gradlew :app:assembleRelease

# or use the helper scripts
./compile_project.command
./compile_release.command
```

> [!WARNING]
> If `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` are not set
> (environment or `~/.gradle/gradle.properties`), the build falls back to the bundled `testkey.keystore` — a
> **public** AOSP test key. That APK is fine for local testing, but anyone could sign an update that Android
> would accept as yours. Never publish an APK signed with it.

## Source code map

| Class | Role |
| ---------------------------- | ----------------------------------------------------------- |
| `a.a.a.ProjectBuilder` | Helper for compiling an entire project |
| `a.a.a.Ix` | Generates `AndroidManifest.xml` |
| `a.a.a.Jx` | Generates the source code of activities |
| `a.a.a.Lx` | Generates component code: listeners, helpers, etc. |
| `a.a.a.Ox` | Generates layout XML files |
| `a.a.a.qq` | Registry of built-in library dependencies |
| `a.a.a.tq` | Compile-dialog steps |
| `a.a.a.yq` | Project file paths |
| `pro.sketchware.*` | Where new features should go, respecting the existing layout |
| `mod.*` | Most community additions live here |

> [!TIP]
> New features that don't require touching other packages belong in `pro.sketchware`, keeping the existing
> directory and file structure. Prefer Java over Kotlin unless Kotlin is genuinely necessary.

## Changes in this fork

This repository is a personal fork. Every improvement is added here as it lands, and the
[website](https://aurenox-global.github.io/Sketchware-Pro/) is updated at the same time.

### 2026-09-22 — code navigation: go to definition and find usages (IDE phase 4)

- **The editor can now jump between files.** "Go to definition" and "Find usages" in the editor menu resolve
  the symbol under the cursor across **all the project's sources** (not just the file you have open) and open the
  result; when there are several matches you get a chooser with file, line and a preview.
- The symbol index now records the **location** of every declaration (file + line), and the navigation goes
  through the LSP scaffolding that was already in the repo (`pro.sketchware.lsp`): a new project-wide provider
  with the existing single-file provider as fallback, plus timeout and background execution.
- Version **v7.0.5.7** (versionCode 157).

### 2026-09-22 — SDK and library completions (IDE phase 3)

- **Completions are no longer limited to your own code:** they now include the **Android SDK classes** (all of
  `android.jar`) and the classes of the **libraries the project uses** (the ones declared in the library manager
  plus any local jars you add).
- The **simple name** is inserted, with the **fully qualified name** shown as the description.
- The index is read once, cached in memory and on disk (invalidated when the jar changes) and warmed up when a
  `.java` file is opened. Order in the list: your project's symbols first, then keywords, then SDK/library classes.
- Version **v7.0.5.6** (versionCode 156), published as
  [v7.0.5.6](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.5.6).

### 2026-09-22 — live Java diagnostics (IDE phase 2)

- **Errors and warnings are underlined as you type.** 1.2 s after you stop typing, the app compiles *only the
  file you are editing* with the Eclipse compiler (ECJ) it already bundles, using the project classpath
  (`android.jar` + `core-lambda-stubs` + your local libraries) and `files/java` as source path. Tapping the mark
  shows the compiler message.
- The analysis runs off the UI thread, never piles up (one in flight at a time, stale results discarded), and
  fails silently: if anything goes wrong, nothing is underlined and the editor behaves as before.
- Version **v7.0.5.5** (versionCode 155), published as
  [v7.0.5.5](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.5.5).

### 2026-09-22 — first slice of the in-app IDE: project-aware autocompletion

- **The Java editor now completes your project's own symbols:** while you type it suggests the classes, methods
  and fields found in the project's `files/java` sources, plus Java keywords.
- The index is built without a compiler — it reads the `.java`/`.kt` files and extracts declarations with regular
  expressions, cached per project and refreshed only when sources change (caps: 400 files, 4000 symbols, 512 KB
  per file).
- The editor language delegates everything else to sora-editor's `JavaLanguage`, so highlighting, indenting and
  bracket matching behave exactly as before. Worst case, the suggestion list simply does not show up.
- Version **v7.0.5.4** (versionCode 154), published as
  [v7.0.5.4](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.5.4). Next: ECJ live diagnostics,
  go-to-definition and an integrated build console.

### 2026-09-21 — the app's GitHub links point to this fork

- **In-app GitHub links now redirect here** instead of upstream: the repository link, the releases link and the
  commits API that feeds the "changes" screen all point to `aurenox-global/Sketchware-Pro`, so the update notice
  checks this fork's releases. `_Mod_README.txt` mentions the fork too.
- Verified inside the built APK: `resources.arsc` carries the three new URLs. Version bumped to **v7.0.5.3**
  (versionCode 153) and published as [v7.0.5.3](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.5.3).

### 2026-09-21 — CI green, and why R8 only failed there

- **Android CI and Verification Baseline now pass.** The last blocker was subtle: the `bundletool` jar that
  GitHub Actions downloads contains embedded `classes.dex` files next to Java bytecode
  (`com/android/tools/build/bundletool/archive/dex/**`), and R8 refuses an archive with both. The local Gradle
  cache has the very same version *without* them, which is why release builds minified fine locally and failed
  only in CI. Filtering that jar is not safe (it drops `aapt2-proto`, and those dex files are what bundletool
  uses to build AABs), so **CI builds with `-PskipMinify`** while local release builds keep R8 enabled.
- Along the way: `google-services.json` mock for the release variant, the public testkey committed so CI signs
  with the same key, ABI splits handled in the workflow, lint baseline regenerated and a real lint error fixed.
- **Published [v7.0.5.2](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.5.2)** with the R8
  APKs (~106 MB per architecture).

### 2026-09-21 — the repository was incomplete (and CI could not compile)

- **Found and fixed a `.gitignore` bug with real consequences.** The pattern `build/` matched *any* directory
  named `build` at any depth, so three real source packages — `mod/hey/studios/build/`, `mod/jbk/build/` and
  `mod/pranav/build/` — were never committed: 8 Java/Kotlin files were missing from the public repository.
  That is why every CI run failed to compile and why a fresh clone could not build at all. Ignore rules are
  now scoped, with explicit exceptions for those packages.
- **CI can build without secrets:** `createMockGoogleServices` now also creates `app/google-services.json`
  (the release variant needs it), and the public AOSP `testkey.keystore` is whitelisted in `.gitignore` and
  committed, so CI signs with the same key as local builds.
- **Workflow updated for ABI splits:** the artifact rename step and the Telegram upload path.
- `docs/dependency-snapshot.lock` regenerated. Version bumped to **v7.0.5.2** (versionCode 152).

### 2026-09-21 — R8 code shrinking (release builds)

- **R8 is enabled for release builds**, cutting each APK from ~129 MB down to ~106 MB. Three things were needed:
  two dependency jars repackaged at build time (`kotlinc-for-sketchware` ships `dalvik/**` classes and `kxml2`
  ships `org/xmlpull/**`, both already provided by Android), the `-dontwarn` rules R8 generates for library
  references that do not exist on Android, and disabling the Crashlytics mapping upload (local builds use a mock
  `google-services.json`). The original signing key is untouched.
- Resource shrinking is still pending: the 29 `getIdentifier()` call sites need a `res/raw/keep.xml` first.

### 2026-09-21 — one APK per architecture (ABI splits)

- **ABI splits enabled.** `assembleRelease` now produces one APK per architecture instead of a single universal
  one, so a device stops downloading the other three sets of native libraries. Verified: every split APK carries
  only its own `lib/<abi>/`, including the bundled `aapt2`.
- **Published as [v7.0.5.1](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.5.1)**, signed with
  the original key so it installs over previous builds.

### 2026-09-21 — signing: the original key stays

- **Releases keep the original signing key, on purpose.** The published APK has to remain updatable over
  existing installs, so release builds are signed with the project's own `testkey.keystore` (SHA-256
  `a40da80a…`), the same identity the published v7.0.5 uses. Verified with `apksigner`: all four split APKs
  carry that exact fingerprint.
- A private 4096-bit keystore was generated and kept at `~/.android-keys/sketchware-pro/release.jks` for the
  day a real distribution identity is wanted. Switching to it would require uninstalling and reinstalling.

### 2026-09-21 — first versioned build (v7.0.5)

- **Build no longer needs a git repo.** `git rev-parse` used to run during configuration and corrupted
  `BuildConfig.GIT_HASH`. It now reads `GIT_HASH` / `GIT_SHORT_HASH` from the environment and only uses git
  when `.git` exists.
- **`SKETCHUB_API_KEY` default.** Without the environment variable, `BuildConfig` contained the literal
  `"null"`. It is now an empty string.
- **Release signing moved out of the source tree.** Credentials come from `RELEASE_STORE_*` / `RELEASE_KEY_*`
  (environment or `~/.gradle/gradle.properties`), with an explicit warning when they are missing.
- **64 empty `catch` blocks annotated.** 44 files were swallowing exceptions silently; they now log through
  `Log.d("SketchwarePro", …)`.
- **Faster builds.** `org.gradle.parallel` and `org.gradle.caching` enabled.
- **Repository published and versioned.** Git history, audited `.gitignore`, public repo and a first release
  with the APK attached.

## Roadmap

| Item | State |
| ------------------------------------------------ | ----------- |
| Build without git, API key default, silent catches | done |
| Release credentials out of the source tree | done |
| Git history, public repo, first release | done |
| Bilingual documentation and website | done |
| Repository complete: restored the source packages hidden by `.gitignore` | done |
| CI green: Android CI + Verification Baseline | done |
| In-app GitHub links point to this fork | done |
| In-app IDE: project-symbol autocompletion (phase 1) | done |
| In-app IDE: ECJ live diagnostics (phase 2) | done |
| In-app IDE: SDK and library completions (phase 3) | done |
| In-app IDE: navigation (go to definition / find usages, phase 4) | done |
| In-app IDE: quick fixes over diagnostics (phase 5) | next |
| R8 running in CI (blocked by the `bundletool` jar) | blocked |
| ABI splits (one APK per architecture) | done |
| Release signing keeps the original key (on purpose) | done |
| R8 code shrinking (release builds) | done |
| Resource shrinking (`res/raw/keep.xml`) | blocked |
| Translations, deprecated APIs, test coverage | planned |

Known blockers, in detail:

- **R8** — `minifyReleaseWithR8` fails because `kotlinc-for-sketchware` ships `dalvik/**` classes that R8
  refuses to treat as program classes. The jar has to be repackaged first.
- **Resource shrinking** — there are 29 `getIdentifier()` call sites, so unused-looking resources would be
  stripped. A `res/raw/keep.xml` has to be written first.
- **`nonTransitiveRClass=true`** — breaks compilation: `mod/jbk/util/OldResourceIdMapper.java` references
  `R.drawable.abc_*` from appcompat, which only exists with transitive R classes.
- **Translations** — 2,239 strings and not a single `values-<language>` folder.
- **Deprecated APIs** — `getColor()` ×126, `onActivityResult` ×63, `startActivityForResult` ×47,
  `getExternalStorageDirectory` ×43.
- **Lint debt** — a 24,169-line baseline that only detects drift, never shrinks.

## Contributing

1. Fork this repository.
2. Make your changes.
3. Test them.
4. Open a pull request.

Commit messages use a type prefix: `feat:`, `fix:`, `style:`, `refactor:`, `test:`, `docs:`, `chore:` —
for example `fix: Fix crash during launch on certain phones`.

## Legal notice

**Sketchware Pro is not open source.** It is *source-available*: you can read the code and submit changes,
but you do not own it. Part of the code may infringe Sketchware's copyright.

It is a community mod made to keep Sketchware alive by the community, for the community, with no harmful
intent toward the original developers. **Publishing Sketchware Pro, unmodified or modified, on Google Play
or any other app store is not permitted.** Use it at your own discretion.

Two modules, `kotlinc` and `build-logic`, come from [CodeAssist](https://github.com/tyron12233/CodeAssist)
and are licensed under GPL-3.0.

## Credits

- Upstream project: [Sketchware-Pro/Sketchware-Pro](https://github.com/Sketchware-Pro/Sketchware-Pro)
- Community: [Discord](http://discord.gg/kq39yhT4rX)
- Original Sketchware by its developers, who made all of this possible

---

<sub>Documentation maintained in English and Spanish. Last updated: 2026-09-21.</sub>
