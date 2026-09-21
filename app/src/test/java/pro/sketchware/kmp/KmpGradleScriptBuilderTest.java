package pro.sketchware.kmp;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KmpGradleScriptBuilderTest {

    @Test
    public void build_generatesExpectedCoreBlocks() {
        KmpProject project = KmpProject.createDefault(
                "test-id",
                "DemoProject",
                "pro.sketchware.demo"
        );

        KmpGradleScripts scripts = KmpGradleScriptBuilder.build(project);

        assertTrue(scripts.settingsGradleKts.contains("pluginManagement"));
        assertTrue(scripts.settingsGradleKts.contains("include(\":shared\")"));
        assertTrue(scripts.settingsGradleKts.contains("include(\":androidApp\")"));
        assertTrue(scripts.settingsGradleKts.contains("include(\":desktopApp\")"));
        assertTrue(scripts.rootBuildGradleKts.contains("alias(libs.plugins.kotlin.multiplatform)"));
        assertTrue(scripts.rootBuildGradleKts.contains("alias(libs.plugins.android.application)"));
        assertTrue(scripts.sharedBuildGradleKts.contains("androidTarget()"));
        assertTrue(scripts.sharedBuildGradleKts.contains("jvm(\"desktop\")"));
        assertTrue(scripts.sharedBuildGradleKts.contains("namespace = \"pro.sketchware.demo.shared\""));
        assertTrue(scripts.androidAppBuildGradleKts.contains("alias(libs.plugins.android.application)"));
        assertTrue(scripts.androidAppBuildGradleKts.contains("implementation(project(\":shared\"))"));
        assertTrue(scripts.desktopAppBuildGradleKts.contains("tasks.register<Jar>(\"desktopJar\")"));
        assertTrue(scripts.versionCatalogToml.contains("[versions]"));
        assertTrue(scripts.versionCatalogToml.contains("kotlin = \"2.0.21\""));
        assertTrue(scripts.versionCatalogToml.contains("[plugins]"));
        assertTrue(scripts.versionCatalogToml.contains("android-application"));
        assertTrue(scripts.versionCatalogToml.contains("kotlin-multiplatform"));
    }
}
