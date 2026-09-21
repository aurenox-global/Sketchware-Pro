package pro.sketchware.kmp

import java.io.File
import java.util.concurrent.TimeUnit

enum class KmpTargetBuildStatus {
    SUCCESS,
    FAILED,
    SKIPPED,
    NOT_SUPPORTED
}

class KmpTargetBuildResult(
    @JvmField val target: KmpTarget,
    @JvmField val status: KmpTargetBuildStatus,
    @JvmField val durationMs: Long,
    @JvmField val artifactPath: String?,
    @JvmField val message: String
)

class KmpBuildOrchestrationReport(
    @JvmField val startedAtMs: Long,
    @JvmField val finishedAtMs: Long,
    @JvmField val results: List<KmpTargetBuildResult>
) {
    fun hasFailures(): Boolean {
        return results.any { it.status == KmpTargetBuildStatus.FAILED }
    }

    fun resultFor(target: KmpTarget): KmpTargetBuildResult? {
        return results.firstOrNull { it.target == target }
    }
}

interface KmpTargetBuildRunner {
    fun run(rootDirectory: File, target: KmpTarget, timeoutMs: Long): KmpTargetBuildResult
}

class KmpGradleTargetBuildRunner : KmpTargetBuildRunner {
    override fun run(rootDirectory: File, target: KmpTarget, timeoutMs: Long): KmpTargetBuildResult {
        val startedAtMs = System.currentTimeMillis()

        val gradleScript = when {
            File(rootDirectory, "gradlew").exists() -> "gradlew"
            File(rootDirectory, "gradlew.bat").exists() -> "gradlew.bat"
            else -> {
                return KmpTargetBuildResult(
                    target,
                    KmpTargetBuildStatus.FAILED,
                    0L,
                    null,
                    "Gradle wrapper script not found"
                )
            }
        }

        val task = resolveTask(rootDirectory, target)
            ?: return KmpTargetBuildResult(
                target,
                KmpTargetBuildStatus.NOT_SUPPORTED,
                0L,
                null,
                "Execution stub pending for ${target.name}"
            )

        val expectedArtifactPath = resolveArtifactPath(rootDirectory, target)

        val command = if (gradleScript.endsWith(".bat")) {
            listOf(File(rootDirectory, gradleScript).absolutePath, "--no-daemon", task)
        } else {
            listOf("sh", File(rootDirectory, gradleScript).absolutePath, "--no-daemon", task)
        }

        return try {
            val process = ProcessBuilder(command)
                .directory(rootDirectory)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (output.length < 4000) {
                        output.append(line).append('\n')
                    }
                }
            }

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                process.waitFor(2, TimeUnit.SECONDS)
                process.destroyForcibly()
                return KmpTargetBuildResult(
                    target,
                    KmpTargetBuildStatus.FAILED,
                    System.currentTimeMillis() - startedAtMs,
                    expectedArtifactPath,
                    "Timed out while executing $task"
                )
            }

            val artifactPath = resolveArtifactPath(rootDirectory, target)
            if (process.exitValue() == 0) {
                KmpTargetBuildResult(
                    target,
                    KmpTargetBuildStatus.SUCCESS,
                    System.currentTimeMillis() - startedAtMs,
                    artifactPath,
                    "Task completed: $task"
                )
            } else {
                KmpTargetBuildResult(
                    target,
                    KmpTargetBuildStatus.FAILED,
                    System.currentTimeMillis() - startedAtMs,
                    artifactPath ?: expectedArtifactPath,
                    "Task failed: $task (exit=${process.exitValue()})"
                )
            }
        } catch (e: Exception) {
            KmpTargetBuildResult(
                target,
                KmpTargetBuildStatus.FAILED,
                System.currentTimeMillis() - startedAtMs,
                expectedArtifactPath,
                e.message ?: "Unknown build runner error"
            )
        }
    }

    private fun resolveTask(rootDirectory: File, target: KmpTarget): String? {
        return when (target) {
            KmpTarget.ANDROID -> {
                if (hasModule(rootDirectory, "androidApp")) {
                    ":androidApp:assembleDebug"
                } else {
                    ":shared:assembleDebug"
                }
            }
            KmpTarget.DESKTOP -> {
                if (hasModule(rootDirectory, "desktopApp")) {
                    ":desktopApp:desktopJar"
                } else {
                    ":shared:desktopJar"
                }
            }
            else -> null
        }
    }

    private fun resolveArtifactPath(rootDirectory: File, target: KmpTarget): String? {
        return when (target) {
            KmpTarget.ANDROID -> resolveAndroidArtifactPath(rootDirectory)
            KmpTarget.DESKTOP -> resolveDesktopArtifactPath(rootDirectory)
            else -> null
        }
    }

    private fun resolveAndroidArtifactPath(rootDirectory: File): String? {
        val androidAppApk = File(rootDirectory, "androidApp/build/outputs/apk/debug/androidApp-debug.apk")
        if (androidAppApk.exists()) {
            return androidAppApk.absolutePath
        }

        val apkDirectory = File(rootDirectory, "androidApp/build/outputs/apk/debug")
        val discoveredApk = apkDirectory.listFiles { file -> file.isFile && file.extension == "apk" }
            ?.sortedBy { it.name }
            ?.firstOrNull()
        if (discoveredApk != null) {
            return discoveredApk.absolutePath
        }

        val sharedAar = File(rootDirectory, "shared/build/outputs/aar/shared-debug.aar")
        if (sharedAar.exists()) {
            return sharedAar.absolutePath
        }

        return if (hasModule(rootDirectory, "androidApp")) {
            androidAppApk.absolutePath
        } else {
            sharedAar.absolutePath
        }
    }

    private fun resolveDesktopArtifactPath(rootDirectory: File): String? {
        val desktopAppDirectory = File(rootDirectory, "desktopApp/build/libs")
        val desktopJar = desktopAppDirectory.listFiles { file ->
            file.isFile && file.extension == "jar" && file.name.contains("desktop")
        }?.sortedBy { it.name }?.firstOrNull()
        if (desktopJar != null) {
            return desktopJar.absolutePath
        }

        val anyDesktopAppJar = desktopAppDirectory.listFiles { file ->
            file.isFile && file.extension == "jar"
        }?.sortedBy { it.name }?.firstOrNull()
        if (anyDesktopAppJar != null) {
            return anyDesktopAppJar.absolutePath
        }

        val sharedLibsDirectory = File(rootDirectory, "shared/build/libs")
        val sharedJar = sharedLibsDirectory.listFiles { file ->
            file.isFile && file.extension == "jar"
        }?.sortedBy { it.name }?.firstOrNull()
        if (sharedJar != null) {
            return sharedJar.absolutePath
        }

        return if (hasModule(rootDirectory, "desktopApp")) {
            File(rootDirectory, "desktopApp/build/libs/desktopApp-desktop.jar").absolutePath
        } else {
            File(rootDirectory, "shared/build/libs/shared-jvm.jar").absolutePath
        }
    }

    private fun hasModule(rootDirectory: File, moduleName: String): Boolean {
        return File(rootDirectory, "$moduleName/build.gradle.kts").exists()
            || File(rootDirectory, "$moduleName/build.gradle").exists()
    }
}

object KmpMultiTargetBuildOrchestrator {
    private val SUPPORTED_EXECUTION_TARGETS = setOf(
        KmpTarget.ANDROID,
        KmpTarget.DESKTOP
    )

    @JvmStatic
    fun orchestratePlan(project: KmpProject, rootDirectory: File): KmpBuildOrchestrationReport {
        val startedAtMs = System.currentTimeMillis()
        val results = project.enabledTargets.map { target ->
            if (SUPPORTED_EXECUTION_TARGETS.contains(target)) {
                KmpTargetBuildResult(
                    target,
                    KmpTargetBuildStatus.SKIPPED,
                    0L,
                    expectedArtifactPath(rootDirectory, target),
                    "Planned target for execution"
                )
            } else {
                KmpTargetBuildResult(
                    target,
                    KmpTargetBuildStatus.NOT_SUPPORTED,
                    0L,
                    null,
                    "Execution stub pending for ${target.name}"
                )
            }
        }

        val finishedAtMs = System.currentTimeMillis()
        return KmpBuildOrchestrationReport(startedAtMs, finishedAtMs, results)
    }

    @JvmStatic
    fun orchestrate(
        project: KmpProject,
        rootDirectory: File,
        timeoutMs: Long,
        runner: KmpTargetBuildRunner?
    ): KmpBuildOrchestrationReport {
        val startedAtMs = System.currentTimeMillis()
        val effectiveRunner = runner ?: KmpGradleTargetBuildRunner()

        val results = project.enabledTargets.map { target ->
            if (!SUPPORTED_EXECUTION_TARGETS.contains(target)) {
                return@map KmpTargetBuildResult(
                    target,
                    KmpTargetBuildStatus.NOT_SUPPORTED,
                    0L,
                    null,
                    "Execution stub pending for ${target.name}"
                )
            }

            try {
                effectiveRunner.run(rootDirectory, target, timeoutMs)
            } catch (e: Exception) {
                KmpTargetBuildResult(
                    target,
                    KmpTargetBuildStatus.FAILED,
                    0L,
                    expectedArtifactPath(rootDirectory, target),
                    e.message ?: "Unexpected orchestrator runner error"
                )
            }
        }

        val finishedAtMs = System.currentTimeMillis()
        return KmpBuildOrchestrationReport(startedAtMs, finishedAtMs, results)
    }

    private fun expectedArtifactPath(rootDirectory: File, target: KmpTarget): String? {
        return when (target) {
            KmpTarget.ANDROID -> {
                if (File(rootDirectory, "androidApp/build.gradle.kts").exists() || File(rootDirectory, "androidApp/build.gradle").exists()) {
                    File(rootDirectory, "androidApp/build/outputs/apk/debug/androidApp-debug.apk").absolutePath
                } else {
                    File(rootDirectory, "shared/build/outputs/aar/shared-debug.aar").absolutePath
                }
            }
            KmpTarget.DESKTOP -> {
                if (File(rootDirectory, "desktopApp/build.gradle.kts").exists() || File(rootDirectory, "desktopApp/build.gradle").exists()) {
                    File(rootDirectory, "desktopApp/build/libs/desktopApp-desktop.jar").absolutePath
                } else {
                    File(rootDirectory, "shared/build/libs/shared-jvm.jar").absolutePath
                }
            }
            else -> null
        }
    }
}
