package pro.sketchware.kmp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class KmpSourceSetHierarchyValidatorTest {

    @Test
    public void validate_defaultHierarchy_isValid() {
        KmpSourceSetHierarchyValidator.Result result =
                KmpSourceSetHierarchyValidator.validate(KmpProjectDefaults.defaultHierarchy());

        assertTrue(result.valid);
        assertFalse(result.hasErrors());
    }

    @Test
    public void validate_withMissingParent_reportsError() {
        Map<String, java.util.List<String>> hierarchy = new LinkedHashMap<>();
        hierarchy.put("commonMain", Collections.emptyList());
        hierarchy.put("androidMain", Arrays.asList("commonMain", "unknownMain"));

        KmpSourceSetHierarchyValidator.Result result =
                KmpSourceSetHierarchyValidator.validate(hierarchy);

        assertFalse(result.valid);
        assertTrue(containsCode(result, "MISSING_PARENT"));
    }

    @Test
    public void validate_withCycle_reportsError() {
        Map<String, java.util.List<String>> hierarchy = new LinkedHashMap<>();
        hierarchy.put("commonMain", Collections.emptyList());
        hierarchy.put("featureMain", Collections.singletonList("featureApi"));
        hierarchy.put("featureApi", Collections.singletonList("featureMain"));

        KmpSourceSetHierarchyValidator.Result result =
                KmpSourceSetHierarchyValidator.validate(hierarchy);

        assertFalse(result.valid);
        assertTrue(containsCode(result, "CYCLE_DETECTED"));
    }

    private static boolean containsCode(KmpSourceSetHierarchyValidator.Result result, String code) {
        for (KmpSourceSetHierarchyValidator.Diagnostic diagnostic : result.diagnostics) {
            if (code.equals(diagnostic.code)) {
                return true;
            }
        }
        return false;
    }
}
