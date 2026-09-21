package pro.sketchware.kmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;

public class KmpMultiTargetBuildOrchestratorTest {

    @Test
    public void orchestratePlan_returnsSupportedAndStubStatuses() {
        KmpProject project = new KmpProject(
                1,
                "sc123",
                "DemoProject",
                "pro.sketchware.demo",
                KmpProject.DEFAULT_KOTLIN_VERSION,
                KmpProject.DEFAULT_COMPOSE_MULTIPLATFORM_VERSION,
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.WASM_JS),
                KmpProjectDefaults.defaultHierarchy(),
                1L,
                1L
        );

        KmpBuildOrchestrationReport report = KmpMultiTargetBuildOrchestrator.orchestratePlan(project, new File("/tmp"));

        assertEquals(2, report.results.size());
        assertEquals(KmpTargetBuildStatus.SKIPPED, report.resultFor(KmpTarget.ANDROID).status);
        assertEquals(KmpTargetBuildStatus.NOT_SUPPORTED, report.resultFor(KmpTarget.WASM_JS).status);
    }

    @Test
    public void orchestrate_withCustomRunner_reportsPerTargetResults() {
        KmpProject project = KmpProject.createDefault("sc999", "DemoProject", "pro.sketchware.demo");

        KmpTargetBuildRunner runner = (rootDirectory, target, timeoutMs) -> new KmpTargetBuildResult(
                target,
                KmpTargetBuildStatus.SUCCESS,
                42L,
                new File(rootDirectory, "out/" + target.name().toLowerCase() + ".artifact").getAbsolutePath(),
                "ok"
        );

        KmpBuildOrchestrationReport report = KmpMultiTargetBuildOrchestrator.orchestrate(
                project,
                new File("/tmp"),
                10_000L,
                runner
        );

        assertFalse(report.hasFailures());
        assertEquals(KmpTargetBuildStatus.SUCCESS, report.resultFor(KmpTarget.ANDROID).status);
        assertEquals(KmpTargetBuildStatus.SUCCESS, report.resultFor(KmpTarget.DESKTOP).status);
        assertNotNull(report.resultFor(KmpTarget.DESKTOP).artifactPath);
    }
}
