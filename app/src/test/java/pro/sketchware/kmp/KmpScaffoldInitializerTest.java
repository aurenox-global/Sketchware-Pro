package pro.sketchware.kmp;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class KmpScaffoldInitializerTest {

    @Test
    public void initialize_createsExpectedFiles() throws Exception {
        File tempDir = Files.createTempDirectory("kmp-scaffold-test").toFile();
        KmpProject project = KmpProject.createDefault(
                "sc123",
                "DemoProject",
                "pro.sketchware.demo"
        );

        KmpScaffoldInitResult result = KmpScaffoldInitializer.initialize(tempDir, project);

        assertTrue(result.success);
        assertTrue(new File(tempDir, "settings.gradle.kts").exists());
        assertTrue(new File(tempDir, "build.gradle.kts").exists());
        assertTrue(new File(tempDir, "gradle.properties").exists());
        assertTrue(new File(tempDir, "gradlew").exists());
        assertTrue(new File(tempDir, "gradlew.bat").exists());
        assertTrue(new File(tempDir, "gradle/wrapper/gradle-wrapper.properties").exists());
        assertTrue(new File(tempDir, "gradle/libs.versions.toml").exists());
        assertTrue(new File(tempDir, "shared/build.gradle.kts").exists());
        assertTrue(new File(tempDir, "androidApp/build.gradle.kts").exists());
        assertTrue(new File(tempDir, "desktopApp/build.gradle.kts").exists());
        assertTrue(new File(tempDir, "androidApp/src/main/AndroidManifest.xml").exists());
        assertTrue(new File(tempDir, "androidApp/src/main/kotlin/pro/sketchware/demo/android/MainActivity.kt").exists());
        assertTrue(new File(tempDir, "desktopApp/src/main/kotlin/pro/sketchware/demo/desktop/Main.kt").exists());
        assertTrue(new File(tempDir, "shared/src/commonMain/kotlin/pro/sketchware/demo/Platform.kt").exists());
        assertTrue(new File(tempDir, "shared/src/commonMain/kotlin/pro/sketchware/demo/GeneratedPlatformBindings.kt").exists());
        assertTrue(new File(tempDir, "shared/src/androidMain/kotlin/pro/sketchware/demo/GeneratedPlatformBindings.kt").exists());
        assertTrue(new File(tempDir, "shared/src/desktopMain/kotlin/pro/sketchware/demo/GeneratedPlatformBindings.kt").exists());

        String platformContent = new String(
            Files.readAllBytes(new File(tempDir, "shared/src/commonMain/kotlin/pro/sketchware/demo/Platform.kt").toPath()),
            StandardCharsets.UTF_8
        );
        String commonBindingsContent = new String(
            Files.readAllBytes(new File(tempDir, "shared/src/commonMain/kotlin/pro/sketchware/demo/GeneratedPlatformBindings.kt").toPath()),
            StandardCharsets.UTF_8
        );
        String desktopMainContent = new String(
            Files.readAllBytes(new File(tempDir, "desktopApp/src/main/kotlin/pro/sketchware/demo/desktop/Main.kt").toPath()),
            StandardCharsets.UTF_8
        );

        assertTrue(platformContent.contains("GeneratedPlatformBindings.logInfo"));
        assertTrue(platformContent.contains("GeneratedPlatformBindings.putKeyValue"));
        assertTrue(platformContent.contains("GeneratedPlatformBindings.getKeyValue"));
        assertTrue(platformContent.contains("GeneratedPlatformBindings.currentTimeMillis"));
        assertTrue(commonBindingsContent.contains("fun logInfo(tag: String, message: String): Unit"));
        assertTrue(commonBindingsContent.contains("fun putKeyValue(key: String, value: String): Unit"));
        assertTrue(commonBindingsContent.contains("fun getKeyValue(key: String): String"));
        assertTrue(commonBindingsContent.contains("fun currentTimeMillis(): Long"));
        assertTrue(desktopMainContent.contains("Platform.sampleBindingsDigest()"));
    }
}
