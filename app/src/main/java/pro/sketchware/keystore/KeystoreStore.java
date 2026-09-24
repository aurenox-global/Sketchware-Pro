package pro.sketchware.keystore;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kellinwood.security.zipsigner.optional.KeyStoreFileManager;
import pro.sketchware.security.SecurePrefs;

/**
 * Keeps user keystores in the app's private storage ({@code filesDir/keystores/<id>.jks}) together
 * with their alias and passwords, which are stored in {@link SecurePrefs} (encrypted).
 *
 * <p>Deliberately not stored on /sdcard: a JKS private key reachable by any app on the device is a
 * leaked signing key.</p>
 */
public final class KeystoreStore {

    private static final String PREFS_NAME = "keystore_manager_secure";
    private static final String KEY_ENTRIES = "entries";
    private static final String DIR_NAME = "keystores";

    private KeystoreStore() {
    }

    public static final class Entry {
        public final String id;
        public final String name;
        public final String alias;
        public final String algorithm;
        public final String storePassword;
        public final String keyPassword;

        Entry(String id, String name, String alias, String algorithm, String storePassword, String keyPassword) {
            this.id = id;
            this.name = name;
            this.alias = alias;
            this.algorithm = algorithm;
            this.storePassword = storePassword;
            this.keyPassword = keyPassword;
        }

        public File file(Context context) {
            return new File(directory(context), id + ".jks");
        }

        public String getStorePassword() {
            return storePassword;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        public String getAlias() {
            return alias;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public String getName() {
            return name;
        }

        public String getId() {
            return id;
        }

        private JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("id", id);
            object.put("name", name);
            object.put("alias", alias);
            object.put("algorithm", algorithm);
            object.put("storePassword", storePassword);
            object.put("keyPassword", keyPassword);
            return object;
        }

        private static Entry fromJson(JSONObject object) {
            return new Entry(
                    object.optString("id"),
                    object.optString("name"),
                    object.optString("alias"),
                    object.optString("algorithm", "SHA256withRSA"),
                    object.optString("storePassword"),
                    object.optString("keyPassword")
            );
        }
    }

    public static File directory(Context context) {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            // Caller will notice when the file can't be written.
            dir = context.getFilesDir();
        }
        return dir;
    }

    private static SharedPreferences prefs(Context context) {
        return SecurePrefs.get(context, PREFS_NAME);
    }

    public static synchronized List<Entry> list(Context context) {
        List<Entry> entries = new ArrayList<>();
        String raw = prefs(context).getString(KEY_ENTRIES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                entries.add(Entry.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Corrupted store: report nothing instead of crashing.
        }
        return entries;
    }

    public static Entry find(Context context, String id) {
        for (Entry entry : list(context)) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    private static void persist(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            try {
                array.put(entry.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply();
    }

    /**
     * Copies {@code source} into private storage and remembers its credentials.
     *
     * @return the stored entry, or {@code null} when {@code source} could not be copied.
     */
    public static synchronized Entry importKeystore(Context context, String name, String alias,
                                                    String algorithm, String storePassword,
                                                    String keyPassword, File source) {
        String id = "ks_" + System.currentTimeMillis();
        File target = new File(directory(context), id + ".jks");
        try {
            copy(source, target);
        } catch (IOException e) {
            target.delete();
            return null;
        }

        Entry entry = new Entry(
                id,
                name,
                alias,
                algorithm == null || algorithm.trim().isEmpty() ? "SHA256withRSA" : algorithm.trim(),
                storePassword,
                keyPassword
        );
        List<Entry> entries = list(context);
        entries.add(entry);
        persist(context, entries);
        return entry;
    }

    public static synchronized void update(Context context, Entry entry) {
        List<Entry> entries = list(context);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(entry.id)) {
                entries.set(i, entry);
                persist(context, entries);
                return;
            }
        }
        entries.add(entry);
        persist(context, entries);
    }

    public static synchronized void delete(Context context, String id) {
        List<Entry> entries = list(context);
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).id.equals(id)) {
                entries.get(i).file(context).delete();
                entries.remove(i);
            }
        }
        persist(context, entries);
    }

    /**
     * @return the certificate's SHA-256 fingerprint, or the error message if the keystore could not
     * be read with the stored credentials.
     */
    public static String certificateSha256(Entry entry, Context context) {
        try {
            KeyStore keyStore = KeyStoreFileManager.loadKeyStore(
                    entry.file(context).getAbsolutePath(), entry.storePassword.toCharArray());
            Certificate certificate = keyStore.getCertificate(entry.alias);
            if (certificate == null) {
                return "Alias not found in keystore";
            }
            // Also fail here (instead of at build time) if the key password is wrong.
            if (keyStore.getKey(entry.alias, entry.keyPassword.toCharArray()) == null) {
                return "Key not readable with the stored key password";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(certificate.getEncoded());
            return formatSha256(hash);
        } catch (Throwable e) {
            // Throwable, not Exception: a broken security provider raises an Error (that is how the
            // R8-stripped spongycastle Mappings showed up), and the caller must not crash for it.
            return "Could not read keystore: " + e.getMessage();
        }
    }

    public static String certificateSubject(Entry entry, Context context) {
        try {
            KeyStore keyStore = KeyStoreFileManager.loadKeyStore(
                    entry.file(context).getAbsolutePath(), entry.storePassword.toCharArray());
            Certificate certificate = keyStore.getCertificate(entry.alias);
            if (certificate instanceof X509Certificate) {
                return ((X509Certificate) certificate).getSubjectX500Principal().getName();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static String formatSha256(byte[] hash) {
        StringBuilder builder = new StringBuilder(hash.length * 3);
        for (int i = 0; i < hash.length; i++) {
            if (i > 0) {
                builder.append(':');
            }
            builder.append(String.format(Locale.US, "%02X", hash[i]));
        }
        return builder.toString();
    }

    private static void copy(File source, File target) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
    }
}
