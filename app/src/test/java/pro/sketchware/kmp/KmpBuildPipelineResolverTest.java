package pro.sketchware.kmp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KmpBuildPipelineResolverTest {

    @Test
    public void resolve_withoutMetadata_returnsCompatibilityMode() {
        KmpBuildPipelineResolution result = KmpBuildPipelineResolver.resolve(false, true, true);

        assertEquals(KmpBuildPipelineMode.ANDROID_COMPATIBILITY, result.mode);
        assertEquals("KMP metadata not detected", result.reason);
    }

    @Test
    public void resolve_withoutBridgeSetting_returnsCompatibilityMode() {
        KmpBuildPipelineResolution result = KmpBuildPipelineResolver.resolve(true, false, true);

        assertEquals(KmpBuildPipelineMode.ANDROID_COMPATIBILITY, result.mode);
        assertEquals("KMP Gradle bridge disabled by build setting", result.reason);
    }

    @Test
    public void resolve_withoutWrapper_returnsCompatibilityMode() {
        KmpBuildPipelineResolution result = KmpBuildPipelineResolver.resolve(true, true, false);

        assertEquals(KmpBuildPipelineMode.ANDROID_COMPATIBILITY, result.mode);
        assertEquals("KMP Gradle wrapper not found", result.reason);
    }

    @Test
    public void resolve_withAllRequirements_returnsExperimentalMode() {
        KmpBuildPipelineResolution result = KmpBuildPipelineResolver.resolve(true, true, true);

        assertEquals(KmpBuildPipelineMode.KMP_GRADLE_EXPERIMENTAL, result.mode);
        assertEquals("KMP metadata detected and Gradle bridge available", result.reason);
    }
}
