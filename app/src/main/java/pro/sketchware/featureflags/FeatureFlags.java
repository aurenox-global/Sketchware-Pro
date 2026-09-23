package pro.sketchware.featureflags;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.EnumMap;
import java.util.Map;

public final class FeatureFlags {

    private static final String PREF_NAME = "feature_flags";
    private static final Map<Key, Boolean> DEFAULTS = new EnumMap<>(Key.class);

    static {
        DEFAULTS.put(Key.CODE_VIEWER_EDIT_AND_SAVE, true);
        DEFAULTS.put(Key.EDITOR_LSP_ABSTRACTION, true);
        DEFAULTS.put(Key.COMMAND_PALETTE_REGISTRY, false);
        DEFAULTS.put(Key.STARTUP_PROFILING_THRESHOLDS, false);
        DEFAULTS.put(Key.ACCESSIBILITY_CHECKS_REPORTING, false);
        DEFAULTS.put(Key.KPI_DASHBOARD_RELEASE_GATES, false);
        DEFAULTS.put(Key.KMP_EXPERIMENTAL_ENABLE, false);
        // Flutter activado por defecto: la funcion ya es estable (modelo de proyecto, andamiaje,
        // compilacion) y la descarga del toolchain NUNCA es silenciosa: siempre pasa por un dialogo
        // de consentimiento con tamanos, red y ubicacion, con errores claros si algo falla.
        // El interruptor sigue existiendo en Ajustes > Feature flags para quien quiera apagarla.
        // applyDefaultsIfMissing() solo escribe el valor si la clave no existe todavia, asi que
        // respeta a los usuarios que ya la hayan cambiado a mano.
        DEFAULTS.put(Key.FLUTTER_EXPERIMENTAL_ENABLE, true);
        DEFAULTS.put(Key.LOCAL_AI_MULTI_ACTIVITY_GENERATION, true);
        DEFAULTS.put(Key.LOCAL_AI_EVENT_CODE_GUIDE, true);
    }

    private FeatureFlags() {
    }

    public static boolean isEnabled(Context context, Key key) {
        SharedPreferences prefs = getPrefs(context);
        boolean defaultValue = DEFAULTS.getOrDefault(key, false);
        return prefs.getBoolean(key.name(), defaultValue);
    }

    public static void setEnabled(Context context, Key key, boolean enabled) {
        getPrefs(context).edit().putBoolean(key.name(), enabled).apply();
    }

    public static void applyDefaultsIfMissing(Context context) {
        SharedPreferences prefs = getPrefs(context);
        SharedPreferences.Editor editor = null;
        for (Map.Entry<Key, Boolean> entry : DEFAULTS.entrySet()) {
            String name = entry.getKey().name();
            if (!prefs.contains(name)) {
                if (editor == null) {
                    editor = prefs.edit();
                }
                editor.putBoolean(name, entry.getValue());
            }
        }
        if (editor != null) {
            editor.apply();
        }
    }

    public enum Key {
        CODE_VIEWER_EDIT_AND_SAVE,
        EDITOR_LSP_ABSTRACTION,
        COMMAND_PALETTE_REGISTRY,
        STARTUP_PROFILING_THRESHOLDS,
        ACCESSIBILITY_CHECKS_REPORTING,
        KPI_DASHBOARD_RELEASE_GATES,
        KMP_EXPERIMENTAL_ENABLE,
        FLUTTER_EXPERIMENTAL_ENABLE,
        LOCAL_AI_MULTI_ACTIVITY_GENERATION,
        LOCAL_AI_EVENT_CODE_GUIDE
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}