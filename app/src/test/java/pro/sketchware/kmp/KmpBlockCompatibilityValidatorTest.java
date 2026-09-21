package pro.sketchware.kmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;

import java.util.Collections;
import java.util.Arrays;

import pro.sketchware.blocks.typing.TypedBlockTypeDiagnostic;
import pro.sketchware.blocks.typing.TypedBlockTypeDiagnosticCode;
import pro.sketchware.blocks.typing.TypedBlockTypeCheckResult;

public class KmpBlockCompatibilityValidatorTest {

    @Test
    public void validate_desktopOnlyProject_withAndroidOnlyBlock_reportsScopeDiagnostic() {
        KmpProject project = new KmpProject(
                1,
                "kmp-compat",
                "CompatProject",
                "pro.sketchware.compat",
                KmpProject.DEFAULT_KOTLIN_VERSION,
                KmpProject.DEFAULT_COMPOSE_MULTIPLATFORM_VERSION,
                Collections.singletonList(KmpTarget.DESKTOP),
                KmpProjectDefaults.defaultHierarchy(),
                1L,
                1L
        );

        BlockBean block = new BlockBean("1", "When %m.view clicked", "c", "viewOnClick");
        TypedBlockTypeCheckResult result = KmpBlockCompatibilityValidator.validate(
                Collections.singletonList(block),
                project
        );

        assertFalse(result.diagnostics.isEmpty());

        boolean found = false;
        for (TypedBlockTypeDiagnostic diagnostic : result.diagnostics) {
            if (diagnostic.code == TypedBlockTypeDiagnosticCode.TARGET_SCOPE_INCOMPATIBLE) {
                found = true;
                break;
            }
        }

        assertTrue(found);
    }

    @Test
    public void validate_nullProject_returnsBaseTypeChecksWithoutScopeFailures() {
        BlockBean block = new BlockBean("1", "sum %d + %d", "d", "mathAdd");
        block.parameters.add("1");
        block.parameters.add("2");

        TypedBlockTypeCheckResult result = KmpBlockCompatibilityValidator.validate(
                Collections.singletonList(block),
                null
        );

        assertEquals(0, result.errorCount());
    }

    @Test
    public void validate_withSelectedTarget_usesSelectedTargetForScopeChecks() {
        KmpProject project = new KmpProject(
                1,
                "kmp-selected-target",
                "CompatProject",
                "pro.sketchware.compat",
                KmpProject.DEFAULT_KOTLIN_VERSION,
                KmpProject.DEFAULT_COMPOSE_MULTIPLATFORM_VERSION,
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.DESKTOP),
                KmpProjectDefaults.defaultHierarchy(),
                1L,
                1L
        );

        BlockBean block = new BlockBean("1", "When %m.view clicked", "c", "viewOnClick");

        TypedBlockTypeCheckResult desktopResult = KmpBlockCompatibilityValidator.validate(
                Collections.singletonList(block),
                project,
                KmpTarget.DESKTOP
        );

        TypedBlockTypeCheckResult androidResult = KmpBlockCompatibilityValidator.validate(
                Collections.singletonList(block),
                project,
                KmpTarget.ANDROID
        );

        boolean desktopHasScopeWarning = false;
        for (TypedBlockTypeDiagnostic diagnostic : desktopResult.diagnostics) {
            if (diagnostic.code == TypedBlockTypeDiagnosticCode.TARGET_SCOPE_INCOMPATIBLE) {
                desktopHasScopeWarning = true;
                break;
            }
        }

        boolean androidHasScopeWarning = false;
        for (TypedBlockTypeDiagnostic diagnostic : androidResult.diagnostics) {
            if (diagnostic.code == TypedBlockTypeDiagnosticCode.TARGET_SCOPE_INCOMPATIBLE) {
                androidHasScopeWarning = true;
                break;
            }
        }

        assertTrue(desktopHasScopeWarning);
        assertFalse(androidHasScopeWarning);
    }
}
