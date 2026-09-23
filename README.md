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
  <img alt="version" src="https://img.shields.io/badge/version-v7.0.10.1-008dcd">
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

### 2026-09-23 — Flutter, experimental: release/AOT on the device, complete assets and real pub (phase 8)

- **Release/AOT on the device is unlocked.** The Dart SDK for Android was built for this fork with **compressed
  pointers** — the flag is set by the architecture name (`arm64c`), not by a command-line option — and as a
  **product** build, because the first token of the snapshot's feature string is decided at compile time
  (`product`, not `release`). Its `gen_snapshot` (4,991,592 B, sha256 `9921983f…`) ships inside the APK, and with it
  **the phone itself produces a `libapp.so` the official engine accepts**. Tested: a **release** APK boots in an
  arm64 Android 14 emulator, the counter goes 0 → 3 on real taps and there is **no DEBUG banner**. Control A/B: the
  same APK with the `gen_snapshot` of the Termux SDK fails with *the snapshot requires 'no-compressed-pointers' but
  the VM has 'compressed-pointers'* — only compressed pointers were missing.
- **The app can run its own tools.** With `targetSdk 34`, SELinux denies `execute_no_trans` for binaries in
  `filesDir` and in `/data/local/tmp`, but **`nativeLibraryDir` works**: the runtime and `gen_snapshot` are packed
  as `lib/arm64-v8a/libdartaotruntime.so` and `libfluttergensnapshot.so`. The app ran the whole AOT from its own
  process, as an untrusted app and without root, in 3.9 s and with the same sha256.
- **The asset bundle is complete.** Material Icons (the real pin is `bin/internal/material_fonts.version` →
  `fonts.zip`, not an engine artifact), the `ink_sparkle.frag` and `stretch_effect.frag` shaders precompiled on the
  host and embedded, `AssetManifest.bin` (`StandardMessageCodec`), `FontManifest.json` and `NOTICES.Z`. Verified on
  the device: the `+` icon is drawn and the shader error is gone.
- **Real pub.** `dart pub get` now runs on the device (the pub client ships in the Dart SDK itself) and resolves
  real pub.dev packages, and the kernel is compiled with the resulting authentic `package_config.json`.
- **Plugins: half-way, stated honestly.** The fork has its own Kotlin/Java plugin compiler (ECJ plus the fork's
  `K2JVMCompiler`) that generates the `GeneratedPluginRegistrant`, merges manifests and pulls AAR dependencies;
  **no plugin has been compiled or started on a device yet** — that is the first pending item.
- **Cost:** the two packed executables add **10.18 MB** to the `arm64-v8a` APK. Other ABIs have no AOT backend and
  say so instead of failing silently. The default mode is still **debug/JIT**, this stays experimental and there is
  still no hot reload.
- **v7.0.10.1 (versionCode 168) — the toolchain no longer says "not installed", and the inherited icon names are told
  apart.** The Flutter toolchain bug was ours, not yours: the app checked the installation by **running**
  `<filesDir>/flutter-toolchain/dart/bin/dart --version`, and SELinux forbids executing an ELF that lives in the app's
  data directory (`execute_no_trans`, `error=13`, `untrusted_app`), so the installer aborted **before** downloading the
  engine and ended with **"Toolchain de Flutter no instalado"** after 91 MB. Now **"installed" is a data criterion**
  (marker `installed.properties` + `gen_kernel_aot.dart.snapshot` + `dartdev_aot.dart.snapshot` +
  `lib/_internal/vm_platform.dill`) and **"can compile" adds a runnable probe** executed from the native libraries
  directory (`libdartaotruntime.so`), so `bin/dart` is never launched again and every message names the **piece, the
  path and the cause**. Verified on an arm64 API 34 emulator: the full install (91 MB `.deb` + engine artifacts) now
  ends at **"Toolchain Flutter: instalado (Dart 3.13.4) · 813.7 MB"**, and the intermediate state is honest too
  (`SDK Dart 3.13.4 extraido … pero NO listo para compilar`) instead of a false "not installed". **A second, pre-existing
  bug surfaced and was fixed:** the pub dependencies were extracted with a `<package>-<version>/` prefix that pub.dev
  tarballs do **not** carry, so 0 files were written and the install died with *"El paquete characters 1.4.1 no se
  extrajo bien"*; it now retries without the prefix. The consent dialog also warns about the real disk space (the
  download is ~307 MB, but extracted it takes ~800 MB). Honest: the **full compile** (pub get + build) inside the app
  could not be automated on the emulator, so it is not claimed; what **is** proven is that the packaged binary runs
  inside the app's own process. **Inherited icon names:** `ic_tune_white` (and family) exists in **no** source of this
  IDE — 2,382 drawables were dumped and the current equivalent is `ic_tune_24`/`ic_mtrl_tune` — so the design preview
  now resolves those old names as a **last resort**, only after project, assets, libraries and the IDE, normalising
  prefixes (`ic_`, `img_`, `icon_`) and colour/size suffixes; with a **single** match it draws it, with several it
  calls it ambiguous and stays red. And **nothing happens silently**: its own group in the detail dialog
  (`Nombre heredado resuelto -> @drawable/ic_tune_white -> @drawable/ic_tune_24`), an **amber info bar** when there is
  no real failure, and the log. Measured: the real case goes from **2 red blocks (2,080 px) to 1 (1,040 px)** and the
  bar reads `1 recurso no encontrado · 1 nombre heredado resuelto`; regression intact. Honest: it covers the names
  whose concept still exists (~19/44 of the families tested); typos, mipmaps and concepts with no current icon stay
  red. Release page: [v7.0.10.1](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.10.1). Full
  write-ups (in Spanish): [docs/flutter-consent.md](docs/flutter-consent.md) (toolchain state/install) and
  [docs/preview-fix.md](docs/preview-fix.md) (round 5).
- **v7.0.10.0 (versionCode 167) — the Dart download you could not find, and preview round 4.** The complaint was that
  the toolchain was nowhere to be seen, so the `FLUTTER_EXPERIMENTAL_ENABLE` flag is now **on by default** (anyone who
  already changed it keeps their value), a **"Flutter (Dart)" card** in the project settings shows the real state
  (`Toolchain Flutter: no instalado (~307.2 MB)` or `instalado (Dart <v>) · <N MB>`) and opens the
  status/download/delete dialog, and the design screen **drawer** has a new item ("Flutter: estado del toolchain")
  that opens the same dialog. With the flag off, both entries stay visible and explain how to turn it on, with a button
  that opens Feature flags — nothing disappears silently. Verified on an arm64 API 34 emulator with screenshots and
  `uiautomator`, without downloading anything; honest: the "installed" branch was never rendered on screen (there was
  no toolchain and the 307 MB were not downloaded). **Preview round 4:** the old message
  `Preview PARCIAL · vistas: 32 · no disponibles: @drawable/ic_tune_white` was read as "32 broken views" when the 32
  were **drawn** views, so the notice is now a short grouped summary
  (`Preview PARCIAL: N recursos no encontrados · M vistas no instanciables`) with a **detail dialog** when you tap the
  bar (groups by cause, count, reason and where it was searched for, 12 per group) and the full detail in logcat; and a
  **real bug is fixed**: the preview **did not look at the project's libraries at all** (local AARs from
  `DependencyResolver`/`ManageLocalLibrary` and extracted built-in libraries) — now it does, measured with a local
  library fixture where `@drawable/ic_tune_white` goes from a red marker (2,076 px) to a drawn icon (6,174 px) and the
  case turns from PARCIAL into `Preview OK · vistas: 32`; broken fonts no longer count as views and the bar is no
  longer covered by the navigation bar; regression fine (`colorPrimary`, backgrounds, styles, images/vectors/gif,
  `MaterialButton`+WebView, real layout). Honest clarification: **`ic_tune_white` exists in no source of this IDE**
  (the APK ships `ic_tune_24`/`ic_mtrl_tune`, it was an inherited name), so that exact name cannot be drawn — and now
  it says so clearly instead of flagging the whole view. Release page:
  [v7.0.10.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.10.0). Full write-ups (in Spanish):
  [docs/preview-fix.md](docs/preview-fix.md) (round 4) and [docs/flutter-consent.md](docs/flutter-consent.md).
- **v7.0.9.0 (versionCode 166) — preview round 3, and a Dart download you decide.** The four reported preview
  symptoms turned out to be **four different root causes**, all measured pixel by pixel on an arm64 API 34 emulator.
  (1) **Text colour**: the editor's colour picker stores `"?" + attr` (e.g. `?colorPrimary`) and the preview only
  understood `?attr/...`, so the text fell back to the default theme colour — measured `(68,71,79)` → `(68,94,145)`.
  (2) **Layouts / linear layouts**: the same resolution failure, but the fallback was the IDE's own opaque white
  `0xFFFFFFFF` "pending" marker, so the linear layout was painted **white** — `(255,255,255)` → `(68,94,145)`.
  (3) **Text styles**: only bold was applied; now italic, bold+italic, monospace and the project's `@font/...` TTFs are
  applied, and a font that does not exist gets a notice. (4) **Images**: only `drawable/<name>.{xml,png,jpg}` was
  searched; now webp/jpeg/gif/bmp, density folders and subfolders, `files/assets`, `app:srcCompat`, **vectors** (drawn
  with the preview's own `PathParser`) and the IDE's default image resolve too, and anything unresolvable shows a red
  marker with the reason. Found and fixed on the way: injected attributes were compared with the `android:` prefix
  against local names, so they **never matched** (`fontFamily`, `srcCompat`, `alpha`, `gravity`, paddings…). Honest
  limits: selectors/ripples/layer-lists are approximated by their last shape, vectors with group transforms are not
  supported, `.9.png` lose their patches, `?attributes` resolve with the IDE theme (not the project's), and nothing
  was tested on a physical phone. And the Dart toolchain (~**307.2 MB**, the real size) is **no longer downloaded
  silently**: `ensureInstalled` — the path every build uses — no longer touches the network and only leaves the message
  to install it from the menu; only the new `allowDownload = true` variant, after consent, downloads. A Material dialog
  shows the state, the missing components **with their size**, "Descarga necesaria: ~307.2 MB", the Wi-Fi warning, where
  it is saved and that it works offline afterwards, with **Download now / Delete toolchain (free X MB, with
  confirmation) / Cancel**; "Compile and run" asks for the same consent and, when cancelled, aborts with a clear
  message and writes nothing to disk. Release page:
  [v7.0.9.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.9.0). Full write-ups (in Spanish):
  [docs/preview-fix.md](docs/preview-fix.md) (round 3) and [docs/flutter-consent.md](docs/flutter-consent.md).
- **v7.0.8.3 (versionCode 165) — three more preview fixes, measured pixel by pixel.** **Project colour resources**
  now resolve: a background written as `@color/...` was stored as the parser's `0xFFFFFFFF` "pending" marker and the
  preview painted it **white** — now the project's `@color/...` and `?attr/...` are resolved (verified with pure blue,
  `(0,0,255)`). The **cold-start crash** is fixed: opening the preview without passing through the design editor died
  with a `NullPointerException` (`ColorsEditorManager` reads the global `DesignActivity.sc_id`, `null` in a fresh
  process) and left a dead screen with no message — the project is now set beforehand and a `try/catch` shows a
  visible error. And the preview now **honours the `xml` extra**, so opening it from the view XML editor shows exactly
  what you are editing, unsaved. Verified pixel by pixel on an arm64 API 34 emulator, light and dark: magenta root
  `(255,0,255)`, a `MaterialButton` visible, and default `TextView`/`Button` text legible in both themes — `(68,71,79)`
  on `(250,249,253)` light, `(196,198,208)` on `(18,19,22)` dark. Release page:
  [v7.0.8.3](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.8.3). Full write-up (in Spanish):
  [docs/preview-fix.md](docs/preview-fix.md).
- **v7.0.8.2 (versionCode 164) — the layout preview paints again.** The preview of View-based designs (the
  HTML/WebView one always worked) showed an empty area — white or black depending on the theme — with only the widgets
  that paint themselves (SeekBar, Switch, icons) and the WebView HTML visible. The cause was a **sentinel**: the IDE's
  own data model stores `0xffffff` for "no colour chosen" (`TextBean.textColor`/`hintColor`, `LayoutBean.backgroundColor`),
  and `LayoutPreviewActivity` applied it as a real colour — so `0x00FFFFFF` (alpha 0) made the text fully transparent and
  wiped the background the widget's own theme would have painted. `ViewPane` made it worse by forcing a **white** canvas
  in preview mode while the widgets were inflated with the IDE theme, which is why the contrast flipped with the theme.
  Now `0xffffff` (and any zero-alpha colour) means "not set" and the theme decides, the preview canvas uses the
  `colorSurface` of the same theme that inflates the views, the inflated root keeps the dimensions declared in the XML,
  and a view the IDE cannot instantiate shows a **red marker** plus a "partial preview" notice instead of a silent gap.
  Verified on an arm64 API 34 emulator: the same layout in light and dark now paints the button (its area was
  255,255,255 with zero glyphs before) and HTML/WebView previews keep working (`Preview OK · vistas: 3 · WebViews: 1`).
  Stated honestly: Material components, Material3 projects and custom project themes are **not** verified on screen, and
  none of it has been tested on a physical phone. Release page:
  [v7.0.8.2](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.8.2). Full write-up (in Spanish):
  [docs/preview-fix.md](docs/preview-fix.md).
- **v7.0.8.1 (versionCode 163) — hotfix for the minified release.** R8 (release shrinking) broke the in-app
  compilation of **any** project: it renamed the `javax.lang.model.SourceVersion` fields, then the ECJ `Messages`
  table, then dropped the `apksig` classes used to sign the APK — three chained failures, all reached through
  reflection that R8 cannot see. Fixed with three `keep` rules in `app/proguard-rules.pro`; verified on an arm64
  API 34 emulator (ECJ → dx → packaging → V3.0 signed APK, `ExceptionInInitializerError` 0 times in logcat).
  Release page: [v7.0.8.1](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.8.1).
- Version **v7.0.8.0** (versionCode 162), release page:
  [v7.0.8.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.8.0). Full write-up (in Spanish) with
  the commands, the measured numbers and the honest pending work: [docs/flutter-fase8.md](docs/flutter-fase8.md).

### 2026-09-23 — Flutter support, experimental: Dart files and builds on the device (phase 7)

- **The editor understands Dart now.** `.dart` files get the TextMate Dart grammar (from the Dart-Code project,
  MIT), its language configuration, bracket matching and **Dart completions**, plus a Flutter icon in the menus.
- **A project type: Flutter.** Behind the `FLUTTER_EXPERIMENTAL_ENABLE` flag — **off by default**, in
  Settings › Feature flags — saving a project seeds `files/flutter/` with `pubspec.yaml`, a Material 3 `lib/main.dart`
  counter app, `assets/`, `.gitignore` and `android/`. The editor then shows a **Flutter menu**: build and run,
  toolchain status and project info.
- **The toolchain is downloaded to the phone.** The Dart SDK for Android (Termux `dart 3.13.4`, a `.deb` unpacked
  with the app's own ar/XZ/tar code) and the Flutter 3.47.5 engine artifacts. Everything runs on the device; the only
  network use is that first download.
- **Honest limitation: release/AOT on the device is blocked.** The Dart SDK's `gen_snapshot` is built **without
  compressed pointers** while the official engine requires them (`Snapshot not compatible … the snapshot requires
  'arm64 android no-compressed-pointers' but the VM has '… compressed-pointers'`), and a compatible `gen_snapshot`
  is only published for linux-x64/darwin-x64/windows-x64 hosts (404 for arm64/android). **Only debug/JIT is usable
  today**, and the UI says so.
- **Proven on a real device.** In an arm64 Android 14 emulator the phone's own Dart compiled the kernel
  (`kernel_blob.bin`, 4.2 s) and an APK packed by hand (`aapt2` + `d8` + `zipalign` + `apksigner`) **started**: Flutter
  activity RESUMED, Material UI rendered and the counter moving on real taps. It is **not** tested on a physical
  phone, on ABIs other than `arm64-v8a`, with hot reload, or with real pub dependency resolution.
- Version **v7.0.7.0** (versionCode 161), release page:
  [v7.0.7.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.7.0). Full write-up (in Spanish) with
  the measured numbers, the raw commands and the pending work: [docs/flutter-fase7.md](docs/flutter-fase7.md).

### 2026-09-22 — layout preview fixed for View-based designs

- **The layout preview no longer shows a blank screen.** Designs built with View elements (AndroidX, widgets) went
  through the design editor's native renderer, which asks the project's internal data for the layout's root
  (`view_root`); when the layout name did not match an entry it returned an **empty root**, so nothing was drawn —
  silently. Layouts with HTML/WebView used the real-views builder, which is why those looked fine.
- Now **every** layout is built with the real-views builder (which works from the XML alone), with the native
  renderer as fallback, and a visible message if both fail instead of a silent blank screen.
- Version **v7.0.6.0** (versionCode 160), published as
  [v7.0.6.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.6.0).

### 2026-09-22 — Kotlin support: completions in .kt files (IDE phase 6)

- **The Kotlin editor now completes too.** `.kt` files finally get the same treatment as Java: suggestions from
  **your project's symbols** (Kotlin classes, objects, `fun` declarations and `val`/`var` properties), **Kotlin
  keywords** and the **SDK/library classes**.
- The index learned Kotlin declarations (`class`/`interface`/`object` with modifiers, `fun` with receiver and
  generics, properties) and only applies those patterns to `.kt` files.
- Everything else (highlighting, indenting, bracket matching) still delegates to the TextMate Kotlin language, so
  the editor behaves exactly as before if anything fails. Kotlin **diagnostics** are not included yet: they need
  the Kotlin compiler, which is far heavier than ECJ — that will be a separate phase.
- Version **v7.0.5.9** (versionCode 159).

### 2026-09-22 — quick fix: import the class that is missing (IDE phase 5)

- **The diagnostics now do something about the error.** When the compiler cannot resolve a type
  (`Button cannot be resolved to a type`), the diagnostic offers a quick fix: **"Importar android.widget.Button"**,
  resolved against the SDK and the project's libraries. Choosing it inserts the `import` after the `package` line.
- Quick fixes are attached to the diagnostic with the editor's document version, so stale ones are discarded
  automatically.
- Version **v7.0.5.8** (versionCode 158).

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
| In-app IDE: quick fixes over diagnostics (phase 5) | done |
| In-app IDE: Kotlin support (phase 6) | done |
| In-app IDE: Kotlin diagnostics (phase 7) | next |
| R8 running in CI (blocked by the `bundletool` jar) | blocked |
| ABI splits (one APK per architecture) | done |
| Release signing keeps the original key (on purpose) | done |
| R8 code shrinking (release builds) | done |
| Resource shrinking (`res/raw/keep.xml`) | blocked |
| Translations, deprecated APIs, test coverage | planned |
| Layout preview blank for View-based designs | fixed |
| Flutter support, experimental (Dart editor, on-device build; debug/JIT only) | experimental |

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
