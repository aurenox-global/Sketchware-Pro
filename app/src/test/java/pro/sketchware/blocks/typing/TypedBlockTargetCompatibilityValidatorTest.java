package pro.sketchware.blocks.typing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TypedBlockTargetCompatibilityValidatorTest {

    @Test
    public void validate_androidOnlyBlockOnDesktop_reportsIncompatibility() {
        BlockBean block = new BlockBean("1", "When %m.view clicked", "c", "viewOnClick");

        List<TypedBlockTypeDiagnostic> diagnostics = new TypedBlockTargetCompatibilityValidator()
                .validate(Collections.singletonList(block), Collections.singletonList("DESKTOP"));

        assertEquals(1, diagnostics.size());
        assertEquals(TypedBlockTypeDiagnosticCode.TARGET_SCOPE_INCOMPATIBLE, diagnostics.get(0).code);
    }

    @Test
    public void validate_commonBlockOnDesktop_doesNotReportIncompatibility() {
        BlockBean block = new BlockBean("1", "sum %d + %d", "d", "mathAdd");

        List<TypedBlockTypeDiagnostic> diagnostics = new TypedBlockTargetCompatibilityValidator()
                .validate(Collections.singletonList(block), Collections.singletonList("DESKTOP"));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void checker_targetAwareOverload_appendsScopeDiagnostics() {
        BlockBean androidOnly = new BlockBean("1", "When %m.view clicked", "c", "viewOnClick");

        TypedBlockTypeCheckResult result = new TypedBlockTypeChecker().check(
                Collections.singletonList(androidOnly),
                Arrays.asList("DESKTOP")
        );

        TypedBlockTypeDiagnostic targetScopeDiagnostic = null;
        for (TypedBlockTypeDiagnostic diagnostic : result.diagnostics) {
            if (diagnostic.code == TypedBlockTypeDiagnosticCode.TARGET_SCOPE_INCOMPATIBLE) {
                targetScopeDiagnostic = diagnostic;
                break;
            }
        }

        assertNotNull(targetScopeDiagnostic);
    }
}
