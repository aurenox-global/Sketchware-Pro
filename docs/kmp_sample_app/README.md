# KMP Sample App

This sample is a minimal Kotlin Multiplatform project with shared logic and Android + Desktop targets.

## Structure

- `shared`: multiplatform source set with expect/actual bindings
- `androidApp`: Android launcher app consuming `:shared`
- `desktopApp`: JVM desktop launcher app consuming `:shared`

## Build

From repository root:

```bash
./gradlew -p docs/kmp_sample_app --no-daemon :androidApp:assembleDebug :desktopApp:desktopJar
```

Expected outputs:

- Android APK at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- Desktop JAR at `desktopApp/build/libs/*desktop*.jar`
