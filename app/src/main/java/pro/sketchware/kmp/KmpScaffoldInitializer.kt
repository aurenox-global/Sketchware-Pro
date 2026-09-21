package pro.sketchware.kmp

import android.content.Context

import java.io.File
import java.io.FileOutputStream

class KmpScaffoldInitResult(
    @JvmField val success: Boolean,
    @JvmField val rootDirectoryPath: String,
    @JvmField val message: String
)

object KmpScaffoldInitializer {
    private const val WRAPPER_JAR_ASSET_PATH = "kmp/gradle/wrapper/gradle-wrapper.jar"

        private val GRADLEW_CONTENT = """
        #!/usr/bin/env sh
        set -eu

                APP_HOME=${'$'}(cd "${'$'}(dirname "${'$'}0")" && pwd)
                CLASSPATH="${'$'}APP_HOME/gradle/wrapper/gradle-wrapper.jar"

                if [ ! -f "${'$'}CLASSPATH" ]; then
                    echo "Gradle wrapper jar not found at ${'$'}CLASSPATH" >&2
          exit 1
        fi

                exec java -classpath "${'$'}CLASSPATH" org.gradle.wrapper.GradleWrapperMain "${'$'}@"
    """

        private val GRADLEW_BAT_CONTENT = """
        @echo off
        setlocal
        set APP_HOME=%~dp0
        set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

        if not exist "%CLASSPATH%" (
          echo Gradle wrapper jar not found at %CLASSPATH%
          exit /b 1
        )

        java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
    """

    private val GRADLE_WRAPPER_PROPERTIES_CONTENT = """
        distributionBase=GRADLE_USER_HOME
        distributionPath=wrapper/dists
        distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
        zipStoreBase=GRADLE_USER_HOME
        zipStorePath=wrapper/dists
    """

    @JvmStatic
    fun initialize(rootDirectory: File, project: KmpProject): KmpScaffoldInitResult {
        return initializeInternal(null, rootDirectory, project)
    }

    @JvmStatic
    fun initialize(context: Context, rootDirectory: File, project: KmpProject): KmpScaffoldInitResult {
        return initializeInternal(context, rootDirectory, project)
    }

    private fun initializeInternal(context: Context?, rootDirectory: File, project: KmpProject): KmpScaffoldInitResult {
        return try {
            if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
                return KmpScaffoldInitResult(
                    false,
                    rootDirectory.absolutePath,
                    "Failed to create KMP root directory"
                )
            }

            val scripts = KmpGradleScriptBuilder.build(project)
            val sharedDirectory = File(rootDirectory, "shared")
            val androidAppDirectory = File(rootDirectory, "androidApp")
            val desktopAppDirectory = File(rootDirectory, "desktopApp")
            val commonMainDirectory = File(sharedDirectory, "src/commonMain/kotlin")
            val packageDirectory = File(commonMainDirectory, project.packageName.replace('.', '/'))
            val desktopMainDirectory = File(
                desktopAppDirectory,
                "src/main/kotlin/${project.packageName.replace('.', '/')}/desktop"
            )

            if (!packageDirectory.exists() && !packageDirectory.mkdirs()) {
                return KmpScaffoldInitResult(
                    false,
                    rootDirectory.absolutePath,
                    "Failed to create commonMain package directory"
                )
            }

            writeText(File(rootDirectory, "settings.gradle.kts"), scripts.settingsGradleKts)
            writeText(File(rootDirectory, "build.gradle.kts"), scripts.rootBuildGradleKts)
            writeText(File(rootDirectory, "gradle.properties"), scripts.gradleProperties)
            writeText(File(sharedDirectory, "build.gradle.kts"), scripts.sharedBuildGradleKts)
            writeText(File(androidAppDirectory, "build.gradle.kts"), scripts.androidAppBuildGradleKts)
            writeText(File(desktopAppDirectory, "build.gradle.kts"), scripts.desktopAppBuildGradleKts)
            writeText(File(rootDirectory, "gradle/libs.versions.toml"), scripts.versionCatalogToml)
            writeText(File(rootDirectory, "gradlew"), GRADLEW_CONTENT.trimIndent() + "\n")
            writeText(File(rootDirectory, "gradlew.bat"), GRADLEW_BAT_CONTENT.trimIndent() + "\n")
            writeText(
                File(rootDirectory, "gradle/wrapper/gradle-wrapper.properties"),
                GRADLE_WRAPPER_PROPERTIES_CONTENT.trimIndent() + "\n"
            )
            writeText(
                File(sharedDirectory, "src/androidMain/AndroidManifest.xml"),
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest />
                """.trimIndent() + "\n"
            )
            writeText(
                File(androidAppDirectory, "src/main/AndroidManifest.xml"),
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application android:label="${project.name}">
                        <activity
                            android:name="${project.packageName}.android.MainActivity"
                            android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """.trimIndent() + "\n"
            )
            writeText(
                File(
                    androidAppDirectory,
                    "src/main/kotlin/${project.packageName.replace('.', '/')}/android/MainActivity.kt"
                ),
                """
                package ${project.packageName}.android

                import android.app.Activity
                import android.os.Bundle

                class MainActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                    }
                }
                """.trimIndent() + "\n"
            )

            File(rootDirectory, "gradlew").setExecutable(true, false)

            val wrapperJarInstalled = copyWrapperJarFromAssets(context, rootDirectory)

            val sampleCommonFile = File(packageDirectory, "Platform.kt")
            if (!sampleCommonFile.exists()) {
                writeText(
                    sampleCommonFile,
                    """
                    package ${project.packageName}

                    object Platform {
                        fun name(): String = GeneratedPlatformBindings.platformName()

                        fun sampleBindingsDigest(): String {
                            val currentPlatform = name()
                            GeneratedPlatformBindings.logInfo("Platform", "Bootstrapping ${'$'}currentPlatform bindings")
                            GeneratedPlatformBindings.putKeyValue("platform.name", currentPlatform)
                            val persistedPlatform = GeneratedPlatformBindings.getKeyValue("platform.name")
                            val currentTimeMs = GeneratedPlatformBindings.currentTimeMillis()
                            return "${'$'}persistedPlatform@${'$'}currentTimeMs"
                        }
                    }
                    """.trimIndent() + "\n"
                )
            }

            val expectActualGeneration = KmpExpectActualGenerator.generate(
                project.packageName,
                project.enabledTargets,
                KmpExpectActualGenerator.starterContracts()
            )
            for (generatedFile in expectActualGeneration.files) {
                val outputFile = File(rootDirectory, generatedFile.relativePath)
                if (!outputFile.exists()) {
                    writeText(outputFile, generatedFile.content)
                }
            }

            if (!desktopMainDirectory.exists() && !desktopMainDirectory.mkdirs()) {
                return KmpScaffoldInitResult(
                    false,
                    rootDirectory.absolutePath,
                    "Failed to create desktopApp package directory"
                )
            }

            val desktopMainFile = File(desktopMainDirectory, "Main.kt")
            if (!desktopMainFile.exists()) {
                writeText(
                    desktopMainFile,
                    """
                    package ${project.packageName}.desktop

                    import ${project.packageName}.Platform

                    fun main() {
                        println(Platform.name())
                        println(Platform.sampleBindingsDigest())
                    }
                    """.trimIndent() + "\n"
                )
            }

            KmpScaffoldInitResult(
                true,
                rootDirectory.absolutePath,
                if (wrapperJarInstalled) {
                    "KMP scaffold initialized with Gradle wrapper"
                } else {
                    "KMP scaffold initialized (Gradle wrapper jar unavailable)"
                }
            )
        } catch (e: Exception) {
            KmpScaffoldInitResult(
                false,
                rootDirectory.absolutePath,
                e.message ?: "Unknown error"
            )
        }
    }

    private fun writeText(file: File, content: String) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        file.writeText(content)
    }

    private fun copyWrapperJarFromAssets(context: Context?, rootDirectory: File): Boolean {
        if (context == null) {
            return false
        }

        val wrapperJarFile = File(rootDirectory, "gradle/wrapper/gradle-wrapper.jar")
        val parent = wrapperJarFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false
        }

        return try {
            context.assets.open(WRAPPER_JAR_ASSET_PATH).use { input ->
                FileOutputStream(wrapperJarFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
