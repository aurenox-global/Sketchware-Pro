#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SMOKE_DIR="$ROOT_DIR/build/kmp-smoke-workflow"

rm -rf "$SMOKE_DIR"
mkdir -p "$SMOKE_DIR/gradle/wrapper"
mkdir -p "$SMOKE_DIR/gradle"
mkdir -p "$SMOKE_DIR/shared/src/commonMain/kotlin/pro/sketchware/smoke"
mkdir -p "$SMOKE_DIR/shared/src/androidMain"
mkdir -p "$SMOKE_DIR/shared/src/androidMain/kotlin/pro/sketchware/smoke"
mkdir -p "$SMOKE_DIR/shared/src/desktopMain/kotlin/pro/sketchware/smoke"
mkdir -p "$SMOKE_DIR/androidApp/src/main/kotlin/pro/sketchware/smoke/android"
mkdir -p "$SMOKE_DIR/desktopApp/src/main/kotlin/pro/sketchware/smoke/desktop"

echo "[KMP smoke] Preparing scaffold at $SMOKE_DIR"

cat > "$SMOKE_DIR/settings.gradle.kts" <<'EOF'
import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "kmp-smoke"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":shared")
include(":androidApp")
include(":desktopApp")
EOF

cat > "$SMOKE_DIR/build.gradle.kts" <<'EOF'
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
}
EOF

cat > "$SMOKE_DIR/gradle.properties" <<'EOF'
kotlin.code.style=official
org.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8
android.useAndroidX=true
EOF

cat > "$SMOKE_DIR/gradle/libs.versions.toml" <<'EOF'
[versions]
kotlin = "2.0.21"
agp = "8.7.3"
coroutines = "1.8.1"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
EOF

cat > "$SMOKE_DIR/shared/build.gradle.kts" <<'EOF'
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget()
    jvm("desktop")
    jvmToolchain(17)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting
        val androidMain by getting
        val desktopMain by getting
    }
}

android {
    namespace = "pro.sketchware.smoke.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
EOF

cat > "$SMOKE_DIR/androidApp/build.gradle.kts" <<'EOF'
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "pro.sketchware.smoke.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "pro.sketchware.smoke.androidapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
}
EOF

cat > "$SMOKE_DIR/desktopApp/build.gradle.kts" <<'EOF'
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
}

application {
    mainClass.set("pro.sketchware.smoke.desktop.MainKt")
}

tasks.register<Jar>("desktopJar") {
    group = "build"
    description = "Assembles runnable Desktop JAR including runtime dependencies."
    archiveClassifier.set("desktop")
    manifest {
        attributes["Main-Class"] = "pro.sketchware.smoke.desktop.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map {
            if (it.isDirectory) {
                it
            } else {
                zipTree(it)
            }
        }
    })
}
EOF

cat > "$SMOKE_DIR/shared/src/androidMain/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest />
EOF

cat > "$SMOKE_DIR/shared/src/commonMain/kotlin/pro/sketchware/smoke/Platform.kt" <<'EOF'
package pro.sketchware.smoke

object Platform {
    fun name(): String = GeneratedPlatformBindings.platformName()

    fun sampleBindingsDigest(): String {
        val currentPlatform = name()
        GeneratedPlatformBindings.logInfo("Platform", "Bootstrapping $currentPlatform bindings")
        GeneratedPlatformBindings.putKeyValue("platform.name", currentPlatform)
        val persistedPlatform = GeneratedPlatformBindings.getKeyValue("platform.name")
        val currentTimeMs = GeneratedPlatformBindings.currentTimeMillis()
        return "$persistedPlatform@$currentTimeMs"
    }
}
EOF

cat > "$SMOKE_DIR/shared/src/commonMain/kotlin/pro/sketchware/smoke/GeneratedPlatformBindings.kt" <<'EOF'
package pro.sketchware.smoke

expect object GeneratedPlatformBindings {
    fun platformName(): String
    fun logInfo(tag: String, message: String): Unit
    fun putKeyValue(key: String, value: String): Unit
    fun getKeyValue(key: String): String
    fun currentTimeMillis(): Long
}
EOF

cat > "$SMOKE_DIR/shared/src/androidMain/kotlin/pro/sketchware/smoke/GeneratedPlatformBindings.kt" <<'EOF'
package pro.sketchware.smoke

actual object GeneratedPlatformBindings {
    actual fun platformName(): String = "Android"
    actual fun logInfo(tag: String, message: String): Unit = println("[$tag] $message")
    actual fun putKeyValue(key: String, value: String): Unit = run { System.setProperty(key, value); Unit }
    actual fun getKeyValue(key: String): String = System.getProperty(key).orEmpty()
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
}
EOF

cat > "$SMOKE_DIR/shared/src/desktopMain/kotlin/pro/sketchware/smoke/GeneratedPlatformBindings.kt" <<'EOF'
package pro.sketchware.smoke

actual object GeneratedPlatformBindings {
    actual fun platformName(): String = "Desktop"
    actual fun logInfo(tag: String, message: String): Unit = println("[$tag] $message")
    actual fun putKeyValue(key: String, value: String): Unit = run { System.setProperty(key, value); Unit }
    actual fun getKeyValue(key: String): String = System.getProperty(key).orEmpty()
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
}
EOF

cat > "$SMOKE_DIR/androidApp/src/main/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="KMP Smoke">
        <activity
            android:name="pro.sketchware.smoke.android.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

cat > "$SMOKE_DIR/androidApp/src/main/kotlin/pro/sketchware/smoke/android/MainActivity.kt" <<'EOF'
package pro.sketchware.smoke.android

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
EOF

cat > "$SMOKE_DIR/desktopApp/src/main/kotlin/pro/sketchware/smoke/desktop/Main.kt" <<'EOF'
package pro.sketchware.smoke.desktop

import pro.sketchware.smoke.Platform

fun main() {
    println(Platform.name())
    println(Platform.sampleBindingsDigest())
}
EOF

cp "$ROOT_DIR/gradlew" "$SMOKE_DIR/gradlew"
cp "$ROOT_DIR/gradlew.bat" "$SMOKE_DIR/gradlew.bat"
cp "$ROOT_DIR/gradle/wrapper/gradle-wrapper.jar" "$SMOKE_DIR/gradle/wrapper/gradle-wrapper.jar"
cp "$ROOT_DIR/gradle/wrapper/gradle-wrapper.properties" "$SMOKE_DIR/gradle/wrapper/gradle-wrapper.properties"
chmod +x "$SMOKE_DIR/gradlew"

if [ -f "$ROOT_DIR/local.properties" ]; then
    cp "$ROOT_DIR/local.properties" "$SMOKE_DIR/local.properties"
elif [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > "$SMOKE_DIR/local.properties"
elif [ -n "${ANDROID_HOME:-}" ]; then
    printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$SMOKE_DIR/local.properties"
else
    echo "[KMP smoke] Android SDK path not found (local.properties, ANDROID_SDK_ROOT, ANDROID_HOME)"
    exit 1
fi

echo "[KMP smoke] Running :androidApp:assembleDebug and :desktopApp:desktopJar"
(
    cd "$SMOKE_DIR"
    ./gradlew --no-daemon :androidApp:assembleDebug :desktopApp:desktopJar
)

APK_PATH="$SMOKE_DIR/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    ALT_APK="$(find "$SMOKE_DIR/androidApp/build/outputs/apk/debug" -maxdepth 1 -type f -name '*.apk' | head -n 1 || true)"
    if [ -z "$ALT_APK" ]; then
        echo "[KMP smoke] Missing Android artifact under $SMOKE_DIR/androidApp/build/outputs/apk/debug"
        exit 1
    fi
    APK_PATH="$ALT_APK"
fi

JAR_PATH="$(find "$SMOKE_DIR/desktopApp/build/libs" -maxdepth 1 -type f -name '*desktop*.jar' | head -n 1 || true)"
if [ -z "$JAR_PATH" ]; then
    JAR_PATH="$(find "$SMOKE_DIR/desktopApp/build/libs" -maxdepth 1 -type f -name '*.jar' | head -n 1 || true)"
fi
if [ -z "$JAR_PATH" ]; then
    echo "[KMP smoke] Missing Desktop artifact under $SMOKE_DIR/desktopApp/build/libs"
    exit 1
fi

echo "[KMP smoke] PASS"
echo "[KMP smoke] Android artifact: $APK_PATH"
echo "[KMP smoke] Desktop artifact: $JAR_PATH"
