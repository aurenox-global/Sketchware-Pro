package pro.sketchware.blocks.typing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TypedBlockScopeTest {

    @Test
    public void inferFromLegacy_androidSignals_returnsAndroidOnly() {
        TypedBlockScope scope = TypedBlockScope.inferFromLegacy(
                "viewOnClick",
                "",
                "When %m.view clicked"
        );

        assertEquals(TypedBlockScope.ANDROID_ONLY, scope);
    }

    @Test
    public void inferFromLegacy_desktopSignals_returnsDesktopOnly() {
        TypedBlockScope scope = TypedBlockScope.inferFromLegacy(
                "desktopDialog",
                "",
                "Show javax.swing dialog"
        );

        assertEquals(TypedBlockScope.DESKTOP_ONLY, scope);
    }

    @Test
    public void fromLegacy_populatesScopeForBackwardCompatibility() {
        TypedBlockSignature signature = TypedBlockSignature.fromLegacy(
                "setText",
                " ",
                "",
                "%m.edittext set text %s"
        );

        assertEquals(TypedBlockScope.ANDROID_ONLY, signature.scope);
    }

    @Test
    public void registerLegacy_withExplicitScope_overridesInferredScope() {
        TypedBlockRegistry registry = new TypedBlockRegistry();

        TypedBlockSignature signature = registry.registerLegacy(
                "platformLog",
                " ",
                "",
                "%m.view show toast %s",
                TypedBlockScope.ALL_EXCEPT_IOS
        );

        assertEquals(TypedBlockScope.ALL_EXCEPT_IOS, signature.scope);
        assertEquals(TypedBlockScope.ALL_EXCEPT_IOS, registry.find("platformLog").scope);
    }
}
