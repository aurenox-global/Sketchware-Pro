package pro.sketchware.kmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public class KmpMigrationAssistantWorkflowTest {

    @Test
    public void run_completesFiveSteps_andStoresMigrationReport() throws Exception {
        File tempDir = Files.createTempDirectory("kmp-migration-assistant").toFile();

        KmpMigrationAssistantSession session = KmpMigrationAssistantWorkflow.run(
                "legacy-wizard",
                Arrays.asList(
                        "com.squareup.retrofit2:retrofit:2.11.0",
                        "androidx.room:room-runtime:2.6.1",
                        "com.google.code.gson:gson:2.11.0",
                        "io.reactivex.rxjava3:rxjava:3.1.9"
                ),
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.IOS_ARM64),
                tempDir
        );

        assertTrue(session.completed);
        assertEquals(5, session.steps.size());
        assertEquals(KmpMigrationAssistantStep.ANALYSIS, session.steps.get(0).step);
        assertEquals(KmpMigrationAssistantStep.VERIFY, session.steps.get(4).step);
        assertNotNull(session.reportPath);

        File reportFile = new File(session.reportPath);
        assertTrue(reportFile.exists());

        String reportJson = new String(Files.readAllBytes(reportFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(reportJson.contains("\"projectId\": \"legacy-wizard\""));
        assertTrue(reportJson.contains("\"summary\""));
        assertTrue(reportJson.contains("\"compatibilityResult\""));
    }
}