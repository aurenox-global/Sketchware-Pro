package pro.sketchware.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

final class AiSecretStore {
    private static final String TAG = "AiSecretStore";
    private static final String SECURE_PREFS_NAME = "local_ai_secure";
    private static final String KEY_CLOUD_API_KEY = "cloud_api_key";

    private AiSecretStore() {
    }

    static String readCloudApiKey(Context context) {
        return trimOrEmpty(getSecurePreferences(context).getString(KEY_CLOUD_API_KEY, ""));
    }

    static void writeCloudApiKey(Context context, String apiKey) {
        getSecurePreferences(context)
                .edit()
                .putString(KEY_CLOUD_API_KEY, trimOrEmpty(apiKey))
                .apply();
    }

    static void migrateLegacyCloudApiKey(Context context, String legacyApiKey) {
        String legacyValue = trimOrEmpty(legacyApiKey);
        if (legacyValue.isEmpty()) {
            return;
        }

        if (readCloudApiKey(context).isEmpty()) {
            writeCloudApiKey(context, legacyValue);
        }
    }

    private static SharedPreferences getSecurePreferences(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREFS_NAME,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Throwable e) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain SharedPreferences", e);
            return context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
