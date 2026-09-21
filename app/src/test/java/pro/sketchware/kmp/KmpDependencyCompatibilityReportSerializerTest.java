package pro.sketchware.kmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class KmpDependencyCompatibilityReportSerializerTest {

    @Test
    public void toLogLine_summarizesStatusCounts() {
        KmpDependencyCompatibilityResult result = KmpDependencyCompatibilityResolver.resolve(
                Arrays.asList(
                        "androidx.appcompat:appcompat:1.7.0",
                        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1",
                        "com.example:legacy-lib:0.1.0"
                ),
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.DESKTOP, KmpTarget.WASM_JS)
        );

        String logLine = KmpDependencyCompatibilityReportSerializer.toLogLine(result);

        assertEquals(
                "KMP dependency compatibility: compatible=1 partiallyCompatible=1 incompatible=0 unknown=1 total=3",
                logLine
        );
    }

    @Test
    public void toJson_emitsNormalizedCoordinatesAndDiagnostics() {
        KmpDependencyCompatibilityResult result = KmpDependencyCompatibilityResolver.resolve(
                Arrays.asList("androidx.appcompat:appcompat:1.7.0"),
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.DESKTOP)
        );

        String json = KmpDependencyCompatibilityReportSerializer.toJson(result);

        assertTrue(json.contains("\"normalizedCoordinate\": \"androidx.appcompat:appcompat\""));
        assertTrue(json.contains("\"code\": \"UNSUPPORTED_TARGET\""));
    }
}