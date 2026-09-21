package mod.hey.studios.compiler.tooling;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;

/**
 * Gradle bridge executed in a separate OS process. This is a safe integration
 * point for future Tooling API-backed sync while keeping the main build thread stable.
 */
public final class GradleToolingBridge {

    private static final long DEFAULT_TIMEOUT_MS = 30_000L;
    private static final int MAX_LOG_CHARS = 4_000;

    private GradleToolingBridge() {
    }

    public static Result syncDependenciesIfEnabled(BuildSettings buildSettings, File projectRootDirectory) {
        if (buildSettings == null) {
            return Result.skipped("build settings unavailable");
        }

        String enabled = buildSettings.getValue(
                BuildSettings.SETTING_ENABLE_GRADLE_TOOLING_BRIDGE,
                ProjectSettings.SETTING_GENERIC_VALUE_FALSE
        );
        if (!ProjectSettings.SETTING_GENERIC_VALUE_TRUE.equals(enabled)) {
            return Result.skipped("disabled by project setting");
        }

        long timeoutMs = parseTimeout(buildSettings.getValue(
                BuildSettings.SETTING_GRADLE_TOOLING_TIMEOUT_MS,
                String.valueOf(DEFAULT_TIMEOUT_MS)
        ));

        return runGradleCommand(projectRootDirectory, timeoutMs, buildAppDependencySyncArgs());
    }

    public static Result syncKmpDependenciesIfEnabled(BuildSettings buildSettings, File kmpRootDirectory) {
        if (buildSettings == null) {
            return Result.skipped("build settings unavailable");
        }

        String enabled = buildSettings.getValue(
                BuildSettings.SETTING_ENABLE_KMP_GRADLE_BRIDGE,
                ProjectSettings.SETTING_GENERIC_VALUE_FALSE
        );
        if (!ProjectSettings.SETTING_GENERIC_VALUE_TRUE.equals(enabled)) {
            return Result.skipped("disabled by project setting");
        }

        long timeoutMs = parseTimeout(buildSettings.getValue(
                BuildSettings.SETTING_KMP_GRADLE_TIMEOUT_MS,
                String.valueOf(DEFAULT_TIMEOUT_MS)
        ));

        return runGradleCommand(kmpRootDirectory, timeoutMs, buildKmpDependencySyncArgs());
    }

    static Result runDependencySync(File projectRootDirectory, long timeoutMs) {
        return runGradleCommand(projectRootDirectory, timeoutMs, buildAppDependencySyncArgs());
    }

    private static Result runGradleCommand(File projectRootDirectory, long timeoutMs, List<String> gradleArguments) {
        if (projectRootDirectory == null || !projectRootDirectory.exists()) {
            return Result.skipped("project root not found");
        }

        List<String> command = buildGradleCommand(projectRootDirectory, gradleArguments);
        if (command.isEmpty()) {
            return Result.skipped("no Gradle executable found");
        }

        long startedAt = System.currentTimeMillis();
        Process process = null;
        CountDownLatch outputLatch = new CountDownLatch(1);
        StringBuilder output = new StringBuilder();

        try {
            process = new ProcessBuilder(command)
                    .directory(projectRootDirectory)
                    .redirectErrorStream(true)
                    .start();

            Process startedProcess = process;
            Thread outputThread = new Thread(() -> {
                try {
                    collectOutput(startedProcess.getInputStream(), output);
                } finally {
                    outputLatch.countDown();
                }
            }, "GradleToolingBridgeOutput");
            outputThread.setDaemon(true);
            outputThread.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                process.waitFor(2, TimeUnit.SECONDS);
                process.destroyForcibly();
                outputLatch.await(2, TimeUnit.SECONDS);

                return Result.timeout(System.currentTimeMillis() - startedAt, sanitize(output.toString()));
            }

            outputLatch.await(2, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            String logs = sanitize(output.toString());
            if (exitCode == 0) {
                return Result.success(System.currentTimeMillis() - startedAt, logs);
            }

            return Result.failure(System.currentTimeMillis() - startedAt, exitCode, logs);
        } catch (Exception e) {
            return Result.failure(System.currentTimeMillis() - startedAt, -1, e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static List<String> buildGradleCommand(File projectRootDirectory, List<String> gradleArguments) {
        File gradleWrapper = new File(projectRootDirectory, "gradlew");
        if (gradleWrapper.exists()) {
            List<String> command = new ArrayList<>();
            command.add("sh");
            command.add(gradleWrapper.getAbsolutePath());
            command.addAll(gradleArguments);
            return command;
        }

        File gradleWrapperBat = new File(projectRootDirectory, "gradlew.bat");
        if (gradleWrapperBat.exists()) {
            List<String> command = new ArrayList<>();
            command.add(gradleWrapperBat.getAbsolutePath());
            command.addAll(gradleArguments);
            return command;
        }

        return new ArrayList<>();
    }

    private static List<String> buildAppDependencySyncArgs() {
        List<String> args = new ArrayList<>();
        args.add("--no-daemon");
        args.add(":app:dependencies");
        args.add("--configuration");
        args.add("debugCompileClasspath");
        return args;
    }

    private static List<String> buildKmpDependencySyncArgs() {
        List<String> args = new ArrayList<>();
        args.add("--no-daemon");
        args.add(":shared:dependencies");
        args.add("--configuration");
        args.add("commonMainApiDependenciesMetadata");
        return args;
    }

    private static long parseTimeout(String timeoutRaw) {
        try {
            long value = Long.parseLong(timeoutRaw);
            return Math.max(1_000L, value);
        } catch (Exception ignored) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    private static void collectOutput(InputStream inputStream, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() >= MAX_LOG_CHARS) {
                    continue;
                }
                int remaining = MAX_LOG_CHARS - output.length();
                if (line.length() > remaining) {
                    output.append(line, 0, Math.max(0, remaining));
                } else {
                    output.append(line);
                }
                output.append('\n');
            }
        } catch (IOException e) {
            if (output.length() < MAX_LOG_CHARS) {
                output.append("[output read error] ").append(e.getMessage());
            }
        }
    }

    private static String sanitize(String logs) {
        if (logs == null || logs.isEmpty()) {
            return "";
        }
        return logs.replace("\r\n", "\n").trim();
    }

    public static final class Result {
        public final boolean attempted;
        public final boolean successful;
        public final boolean timedOut;
        public final long durationMs;
        public final int exitCode;
        public final String details;

        private Result(boolean attempted,
                       boolean successful,
                       boolean timedOut,
                       long durationMs,
                       int exitCode,
                       String details) {
            this.attempted = attempted;
            this.successful = successful;
            this.timedOut = timedOut;
            this.durationMs = durationMs;
            this.exitCode = exitCode;
            this.details = details == null ? "" : details;
        }

        private static Result skipped(String reason) {
            return new Result(false, true, false, 0L, 0, reason);
        }

        private static Result success(long durationMs, String details) {
            return new Result(true, true, false, durationMs, 0, details);
        }

        private static Result failure(long durationMs, int exitCode, String details) {
            return new Result(true, false, false, durationMs, exitCode, details);
        }

        private static Result timeout(long durationMs, String details) {
            return new Result(true, false, true, durationMs, -1, details);
        }

        public String toLogLine() {
            if (!attempted) {
                return "Gradle tooling bridge skipped: " + details;
            }
            if (timedOut) {
                return "Gradle tooling bridge timeout after " + durationMs + " ms";
            }
            if (!successful) {
                return "Gradle tooling bridge failed (exit=" + exitCode + ") after " + durationMs + " ms";
            }
            return String.format(Locale.US,
                    "Gradle tooling bridge sync OK in %d ms",
                    durationMs);
        }
    }
}
