package pro.sketchware.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * SharedPreferences backed by {@link EncryptedSharedPreferences} (AES256_GCM master key stored in
 * the device keystore), with a graceful fallback to plain private SharedPreferences when the
 * encrypted implementation is unavailable on the device.
 *
 * <p>Same pattern already used by {@code pro.sketchware.ai.AiSecretStore}; extracted so that other
 * features (keystore passwords, for example) can reuse it.</p>
 *
 * <p>Warning: {@link EncryptedSharedPreferences} ties its keys to the device keystore. A backup
 * restored onto another device cannot be decrypted, which is why the plain fallback exists. Do not
 * store anything irrecoverable in here.</p>
 */
public final class SecurePrefs {

    private static final String TAG = "SecurePrefs";

    private SecurePrefs() {
    }

    public static SharedPreferences get(Context context, String name) {
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    name,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Throwable e) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain SharedPreferences", e);
            return context.getSharedPreferences(name, Context.MODE_PRIVATE);
        }
    }
}
