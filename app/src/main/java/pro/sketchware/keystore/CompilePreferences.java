package pro.sketchware.keystore;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import pro.sketchware.security.SecurePrefs;

/**
 * Remembers what the user chose the last time they compiled something (format and signing mode),
 * so the unified "Compile" dialog comes up already filled in instead of asking again every build.
 *
 * <p>Keystore passwords entered for a keystore <em>file</em> (as opposed to a saved keystore, whose
 * credentials live in {@link KeystoreStore}) are kept here, encrypted with
 * {@link SecurePrefs}, so a path typed once does not have to be typed again either.</p>
 */
public final class CompilePreferences {

    private static final String PREFS_NAME = "compile_prefs";

    private static final String KEY_FORMAT = "format";
    private static final String KEY_SIGNING_MODE = "signing_mode";
    private static final String KEY_KEYSTORE_ID = "keystore_id";
    private static final String KEY_CUSTOM_PATH = "custom_path";
    private static final String KEY_CUSTOM_ALIAS = "custom_alias";
    private static final String KEY_CUSTOM_STORE_PASSWORD = "custom_store_password";
    private static final String KEY_CUSTOM_KEY_PASSWORD = "custom_key_password";
    private static final String KEY_CUSTOM_ALGORITHM = "custom_algorithm";

    private CompilePreferences() {
    }

    private static SharedPreferences prefs(Context context) {
        return SecurePrefs.get(context, PREFS_NAME);
    }

    private static String get(Context context, String key) {
        return prefs(context).getString(key, "");
    }

    private static void put(Context context, String key, String value) {
        prefs(context).edit().putString(key, value == null ? "" : value).apply();
    }

    public static String getFormat(Context context) {
        return get(context, KEY_FORMAT);
    }

    public static void setFormat(Context context, String format) {
        put(context, KEY_FORMAT, format);
    }

    public static String getSigningMode(Context context) {
        return get(context, KEY_SIGNING_MODE);
    }

    public static void setSigningMode(Context context, String signingMode) {
        put(context, KEY_SIGNING_MODE, signingMode);
    }

    public static String getKeystoreId(Context context) {
        return get(context, KEY_KEYSTORE_ID);
    }

    public static void setKeystoreId(Context context, String keystoreId) {
        put(context, KEY_KEYSTORE_ID, keystoreId);
    }

    public static String getCustomKeystorePath(Context context) {
        return get(context, KEY_CUSTOM_PATH);
    }

    public static String getCustomAlias(Context context) {
        return get(context, KEY_CUSTOM_ALIAS);
    }

    public static String getCustomStorePassword(Context context) {
        return get(context, KEY_CUSTOM_STORE_PASSWORD);
    }

    public static String getCustomKeyPassword(Context context) {
        return get(context, KEY_CUSTOM_KEY_PASSWORD);
    }

    public static String getCustomAlgorithm(Context context) {
        return get(context, KEY_CUSTOM_ALGORITHM);
    }

    /**
     * Remembers a keystore <em>file</em>'s details. Never called for saved keystores, whose
     * credentials already live (encrypted) in {@link KeystoreStore}.
     */
    public static void saveCustomKeystore(Context context, String path, String alias,
                                          String storePassword, String keyPassword, String algorithm) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putString(KEY_CUSTOM_PATH, path == null ? "" : path);
        editor.putString(KEY_CUSTOM_ALIAS, alias == null ? "" : alias);
        editor.putString(KEY_CUSTOM_STORE_PASSWORD, storePassword == null ? "" : storePassword);
        editor.putString(KEY_CUSTOM_KEY_PASSWORD, keyPassword == null ? "" : keyPassword);
        editor.putString(KEY_CUSTOM_ALGORITHM, algorithm == null ? "" : algorithm);
        editor.apply();
    }

    public static boolean hasCustomKeystore(Context context) {
        return !TextUtils.isEmpty(getCustomKeystorePath(context));
    }
}
