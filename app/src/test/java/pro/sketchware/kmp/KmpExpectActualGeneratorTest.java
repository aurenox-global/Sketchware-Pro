package pro.sketchware.kmp;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class KmpExpectActualGeneratorTest {

    @Test
    public void generate_emitsExpectAndActualFilesForAndroidAndDesktop() {
        KmpExpectActualGenerationResult result = KmpExpectActualGenerator.generate(
                "pro.sketchware.demo",
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.DESKTOP),
                KmpExpectActualGenerator.starterContracts()
        );

        KmpExpectActualGeneratedFile commonFile = result.findFile(
                "shared/src/commonMain/kotlin/pro/sketchware/demo/GeneratedPlatformBindings.kt"
        );
        KmpExpectActualGeneratedFile androidFile = result.findFile(
                "shared/src/androidMain/kotlin/pro/sketchware/demo/GeneratedPlatformBindings.kt"
        );
        KmpExpectActualGeneratedFile desktopFile = result.findFile(
                "shared/src/desktopMain/kotlin/pro/sketchware/demo/GeneratedPlatformBindings.kt"
        );

        assertNotNull(commonFile);
        assertNotNull(androidFile);
        assertNotNull(desktopFile);
        assertTrue(commonFile.content.contains("expect object GeneratedPlatformBindings"));
        assertTrue(commonFile.content.contains("fun logInfo(tag: String, message: String): Unit"));
        assertTrue(commonFile.content.contains("fun putKeyValue(key: String, value: String): Unit"));
        assertTrue(commonFile.content.contains("fun getKeyValue(key: String): String"));
        assertTrue(commonFile.content.contains("fun currentTimeMillis(): Long"));
        assertTrue(androidFile.content.contains("actual fun platformName(): String = \"Android\""));
        assertTrue(androidFile.content.contains("actual fun logInfo(tag: String, message: String): Unit = println(\"[$tag] $message\")"));
        assertTrue(androidFile.content.contains("actual fun putKeyValue(key: String, value: String): Unit = run { System.setProperty(key, value); Unit }"));
        assertTrue(androidFile.content.contains("actual fun getKeyValue(key: String): String = System.getProperty(key).orEmpty()"));
        assertTrue(androidFile.content.contains("actual fun currentTimeMillis(): Long = System.currentTimeMillis()"));
        assertTrue(desktopFile.content.contains("actual fun platformName(): String = \"Desktop\""));
        assertTrue(desktopFile.content.contains("actual fun logInfo(tag: String, message: String): Unit = println(\"[$tag] $message\")"));
    }

    @Test
    public void starterContracts_withOverrides_appliesTemplateHooks() {
        KmpPlatformAwareBlockContractOverride override = new KmpPlatformAwareBlockContractOverride(
                "logger_log",
                "println(\"ANDROID-$message\")",
                "println(\"DESKTOP-$message\")",
                "println(message)"
        );

        KmpExpectActualGenerationResult result = KmpExpectActualGenerator.generate(
                "pro.sketchware.demo",
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.DESKTOP),
                KmpExpectActualGenerator.starterContracts(Collections.singletonList(override))
        );

        KmpExpectActualGeneratedFile androidFile = result.findFile(
                "shared/src/androidMain/kotlin/pro/sketchware/demo/GeneratedPlatformBindings.kt"
        );
        KmpExpectActualGeneratedFile desktopFile = result.findFile(
                "shared/src/desktopMain/kotlin/pro/sketchware/demo/GeneratedPlatformBindings.kt"
        );

        assertNotNull(androidFile);
        assertNotNull(desktopFile);
        assertTrue(androidFile.content.contains("actual fun logInfo(tag: String, message: String): Unit = println(\"ANDROID-$message\")"));
        assertTrue(desktopFile.content.contains("actual fun logInfo(tag: String, message: String): Unit = println(\"DESKTOP-$message\")"));
    }

    @Test
    public void generate_withMissingTargetImplementation_usesFallbackTodo() {
        KmpPlatformAwareBlockContract contract = new KmpPlatformAwareBlockContract(
                "logger_log",
                "log",
                Collections.singletonList(new KmpPlatformAwareBlockParameter("message", "String")),
                "Unit",
                "println(message)",
                null,
                null
        );

        KmpExpectActualGenerationResult result = KmpExpectActualGenerator.generate(
                "pro.sketchware.demo",
                Collections.singletonList(KmpTarget.DESKTOP),
                Collections.singletonList(contract)
        );

        KmpExpectActualGeneratedFile desktopFile = result.findFile(
                "shared/src/desktopMain/kotlin/pro/sketchware/demo/GeneratedPlatformBindings.kt"
        );
        assertNotNull(desktopFile);
        assertTrue(desktopFile.content.contains("TODO(\"Missing actual implementation for contract 'logger_log' on DESKTOP\")"));
    }
}
