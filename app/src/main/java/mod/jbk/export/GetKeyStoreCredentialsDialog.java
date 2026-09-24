package mod.jbk.export;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import a.a.a.wq;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.DialogKeystoreCredentialsBinding;
import pro.sketchware.keystore.CompilePreferences;
import pro.sketchware.keystore.KeystoreStore;
import pro.sketchware.utility.SketchwareUtil;

/**
 * The one dialog used to compile something: what to build (APK debug / APK release / AAB, when the
 * caller offers a choice) and how to sign the result (a saved keystore, a keystore file, the testkey,
 * or not at all).
 *
 * <p>Both choices are remembered in {@link CompilePreferences}, so a normal build is two taps and
 * never a password prompt again.</p>
 */
public class GetKeyStoreCredentialsDialog {

    /**
     * What the user wants to get out of the build.
     */
    public enum Format {
        APK_DEBUG("APK (debug)"),
        APK_RELEASE("APK (release)"),
        AAB("AAB (Android App Bundle)");

        private final String label;

        Format(String label) {
            this.label = label;
        }

        String getLabel() {
            return label;
        }
    }

    private final Activity activity;
    private final MaterialAlertDialogBuilder dialog;
    private final DialogKeystoreCredentialsBinding binding;
    private CredentialsReceiver receiver;
    private CompileRequestReceiver compileReceiver;
    private SigningMode mode;

    /**
     * Formats the caller offers, in order. {@code null} or empty hides the format selector
     * (the caller already fixed what it is going to build).
     */
    private final Format[] availableFormats;
    private Format format;

    /**
     * Keystores saved from Settings → Keystore manager. Empty when the user has none yet.
     */
    private final List<KeystoreStore.Entry> savedKeystores = new LinkedList<>();
    private KeystoreStore.Entry selectedKeystore;

    public GetKeyStoreCredentialsDialog(Activity activity, int iconResourceId, String title, String noticeText) {
        this(activity, iconResourceId, title, noticeText, null, null);
    }

    /**
     * @param defaultFormat the format selected when the user has no remembered choice yet. Only
     *                      used when {@code formats} is not empty.
     * @param formats       the formats to offer, or {@code null} to not offer any choice.
     */
    public GetKeyStoreCredentialsDialog(Activity activity, int iconResourceId, String title, String noticeText,
                                        Format defaultFormat, Format[] formats) {
        this.activity = activity;
        this.availableFormats = formats != null && formats.length > 0 ? formats : null;
        dialog = new MaterialAlertDialogBuilder(activity);
        dialog.setIcon(iconResourceId);
        dialog.setTitle(title);
        dialog.setMessage(noticeText);
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_next), null);

        binding = DialogKeystoreCredentialsBinding.inflate(LayoutInflater.from(activity));
        dialog.setView(binding.getRoot());

        savedKeystores.addAll(KeystoreStore.list(activity));

        setupFormatSpinner(defaultFormat);
        setupSpinner(activity);
        setupKeystoreSpinner(activity);
        binding.etSigningAlgorithm.setText("SHA256withRSA");
        restoreLastChoice();
    }

    private void setupFormatSpinner(Format defaultFormat) {
        if (availableFormats == null) {
            binding.tilFormat.setVisibility(android.view.View.GONE);
            return;
        }

        binding.tilFormat.setVisibility(View.VISIBLE);

        LinkedList<String> labels = new LinkedList<>();
        for (Format available : availableFormats) {
            labels.add(available.getLabel());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, labels);
        binding.actFormat.setAdapter(adapter);
        binding.actFormat.setOnItemClickListener((parent, view, position, id) -> setFormat(availableFormats[position], true));

        Format remembered = formatFromName(CompilePreferences.getFormat(activity));
        setFormat(remembered != null ? remembered : (defaultFormat != null ? defaultFormat : availableFormats[0]), false);
    }

    private Format formatFromName(String name) {
        if (availableFormats == null || TextUtils.isEmpty(name)) {
            return null;
        }
        for (Format available : availableFormats) {
            if (available.name().equals(name)) {
                return available;
            }
        }
        return null;
    }

    private void setFormat(Format format, boolean remember) {
        this.format = format;
        binding.actFormat.setText(format.getLabel(), false);
        if (remember) {
            CompilePreferences.setFormat(activity, format.name());
        }
        updateInputFieldsState();
    }

    /**
     * Restores format and signing mode from the last compile, so nothing has to be retyped.
     */
    private void restoreLastChoice() {
        SigningMode rememberedMode = signingModeFromName(CompilePreferences.getSigningMode(activity));
        if (rememberedMode != null) {
            setMode(rememberedMode, false);
        } else {
            // Default to the saved-keystore flow: the point of the manager is not to retype passwords.
            setMode(savedKeystores.isEmpty() ? SigningMode.CUSTOM_KEY_STORE : SigningMode.SAVED_KEY_STORE, false);
        }

        if (mode == SigningMode.SAVED_KEY_STORE) {
            String rememberedId = CompilePreferences.getKeystoreId(activity);
            KeystoreStore.Entry entry = rememberedId.isEmpty() ? null : KeystoreStore.find(activity, rememberedId);
            if (entry != null) {
                applyKeystore(entry);
            }
        } else if (mode == SigningMode.CUSTOM_KEY_STORE && CompilePreferences.hasCustomKeystore(activity)) {
            // A keystore file that was used before: its path/alias/passwords come back too.
            selectedKeystore = null;
            binding.etKeystorePath.setText(CompilePreferences.getCustomKeystorePath(activity));
            binding.etAlias.setText(CompilePreferences.getCustomAlias(activity));
            binding.etStorePassword.setText(CompilePreferences.getCustomStorePassword(activity));
            binding.etPassword.setText(CompilePreferences.getCustomKeyPassword(activity));
            String rememberedAlgorithm = CompilePreferences.getCustomAlgorithm(activity);
            if (!TextUtils.isEmpty(rememberedAlgorithm)) {
                binding.etSigningAlgorithm.setText(rememberedAlgorithm);
            }
        }
    }

    private SigningMode signingModeFromName(String name) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        for (SigningMode signingMode : SigningMode.values()) {
            if (signingMode.name().equals(name)) {
                return signingMode;
            }
        }
        return null;
    }

    private void setupSpinner(Activity activity) {
        String[] dropdownItems = getDropdownItems();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, dropdownItems);
        binding.actSigningMode.setAdapter(adapter);
        binding.actSigningMode.setOnItemClickListener((parent, view, position, id) -> setMode(SigningMode.values()[position], true));
    }

    private void setupKeystoreSpinner(Activity activity) {
        LinkedList<String> labels = new LinkedList<>();
        for (KeystoreStore.Entry entry : savedKeystores) {
            labels.add(entry.getName());
        }
        if (labels.isEmpty()) {
            labels.add("No keystore saved yet");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, labels);
        binding.actKeystore.setAdapter(adapter);
        binding.actKeystore.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < savedKeystores.size()) {
                applyKeystore(savedKeystores.get(position));
                CompilePreferences.setKeystoreId(activity, savedKeystores.get(position).getId());
            }
        });
    }

    private void applyKeystore(KeystoreStore.Entry entry) {
        selectedKeystore = entry;
        binding.etKeystorePath.setText(entry.file(activity).getAbsolutePath());
        binding.etAlias.setText(entry.getAlias());
        binding.etStorePassword.setText(entry.getStorePassword());
        binding.etPassword.setText(entry.getKeyPassword());
        binding.etSigningAlgorithm.setText(entry.getAlgorithm());
    }

    private String[] getDropdownItems() {
        LinkedList<String> labels = new LinkedList<>();
        for (SigningMode mode : SigningMode.values()) {
            labels.add(mode.label);
        }
        return labels.toArray(new String[0]);
    }

    private void setMode(SigningMode mode, boolean remember) {
        this.mode = mode;
        binding.actSigningMode.setText(mode.label, false);
        if (remember) {
            CompilePreferences.setSigningMode(activity, mode.name());
        }
        if (mode == SigningMode.SAVED_KEY_STORE && selectedKeystore == null && !savedKeystores.isEmpty()) {
            String rememberedId = CompilePreferences.getKeystoreId(activity);
            KeystoreStore.Entry remembered = rememberedId.isEmpty() ? null : KeystoreStore.find(activity, rememberedId);
            applyKeystore(remembered != null ? remembered : savedKeystores.get(0));
        }
        if (mode == SigningMode.CUSTOM_KEY_STORE && TextUtils.isEmpty(binding.etKeystorePath.getText())) {
            binding.etKeystorePath.setText(wq.j());
        }
        updateInputFieldsState();
    }

    /**
     * @return whether the currently selected format is the quick debug build (Run), which is always
     * signed with the testkey: there is no signing choice to offer for it.
     */
    private boolean isDebugFormat() {
        return format == Format.APK_DEBUG;
    }

    private void updateInputFieldsState() {
        if (isDebugFormat()) {
            binding.actSigningMode.setText(SigningMode.TESTKEY.label, false);
            binding.tilSigningMode.setEnabled(false);
            binding.tilSigningMode.setHelperText("Debug builds are always signed with the testkey");
            binding.tilKeystore.setVisibility(android.view.View.GONE);
            binding.tilKeystorePath.setVisibility(android.view.View.GONE);
            binding.tilStorePassword.setVisibility(android.view.View.GONE);
            binding.tilAlias.setVisibility(android.view.View.GONE);
            binding.tilPassword.setVisibility(android.view.View.GONE);
            binding.tilSigningAlgorithm.setVisibility(android.view.View.GONE);
            return;
        }
        binding.tilSigningMode.setEnabled(true);
        binding.tilSigningMode.setHelperText(null);
        // The debug format overwrote this field's text with its fixed "testkey" label; put the
        // actually selected mode back, or the dialog would claim the wrong thing.
        if (mode != null) {
            binding.actSigningMode.setText(mode.label, false);
        }

        boolean savedKeystore = mode == SigningMode.SAVED_KEY_STORE;
        boolean customKeystore = mode == SigningMode.CUSTOM_KEY_STORE;
        boolean signingWithKeyStore = savedKeystore || customKeystore;

        binding.tilKeystore.setEnabled(savedKeystore);
        binding.tilKeystore.setVisibility(savedKeystore ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.tilKeystorePath.setVisibility(signingWithKeyStore ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.tilStorePassword.setVisibility(signingWithKeyStore ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.tilAlias.setVisibility(signingWithKeyStore ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.tilPassword.setVisibility(signingWithKeyStore ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.tilSigningAlgorithm.setVisibility(signingWithKeyStore ? android.view.View.VISIBLE : android.view.View.GONE);

        boolean editable = customKeystore;
        binding.etKeystorePath.setEnabled(editable);
        binding.etKeystorePath.setFocusable(editable);
        binding.tilAlias.setEnabled(signingWithKeyStore);
        binding.tilPassword.setEnabled(signingWithKeyStore);
        binding.tilSigningAlgorithm.setEnabled(signingWithKeyStore);
        binding.tilStorePassword.setEnabled(signingWithKeyStore);
    }

    private void onNextButtonClick(DialogInterface dialogInterface) {
        if (isDebugFormat()) {
            dialogInterface.dismiss();
            deliver(new Credentials("SHA256withRSA"));
            return;
        }

        if (mode == SigningMode.SAVED_KEY_STORE || mode == SigningMode.CUSTOM_KEY_STORE) {
            if (validateInputs()) {
                String path = Helper.getText(binding.etKeystorePath);
                String alias = Helper.getText(binding.etAlias);
                String storePassword = Helper.getText(binding.etStorePassword);
                String keyPassword = Helper.getText(binding.etPassword);
                String algorithm = Helper.getText(binding.etSigningAlgorithm);

                CompilePreferences.setSigningMode(activity, mode.name());
                if (mode == SigningMode.SAVED_KEY_STORE && selectedKeystore != null) {
                    CompilePreferences.setKeystoreId(activity, selectedKeystore.getId());
                } else {
                    CompilePreferences.saveCustomKeystore(activity, path, alias, storePassword, keyPassword, algorithm);
                }
                if (format != null) {
                    CompilePreferences.setFormat(activity, format.name());
                }

                dialogInterface.dismiss();
                deliver(new Credentials(path, storePassword, alias, keyPassword, algorithm));
            }
        } else if (mode == SigningMode.TESTKEY) {
            CompilePreferences.setSigningMode(activity, mode.name());
            if (format != null) {
                CompilePreferences.setFormat(activity, format.name());
            }
            dialogInterface.dismiss();
            deliver(new Credentials(Helper.getText(binding.etSigningAlgorithm)));
        } else if (mode == SigningMode.DONT_SIGN) {
            CompilePreferences.setSigningMode(activity, mode.name());
            if (format != null) {
                CompilePreferences.setFormat(activity, format.name());
            }
            dialogInterface.dismiss();
            deliver(null);
        }
    }

    /**
     * Hands the user's choice to whichever listener was registered.
     */
    private void deliver(Credentials credentials) {
        if (compileReceiver != null) {
            compileReceiver.onCompile(new CompileRequest(format, credentials));
        } else if (receiver != null) {
            receiver.gotCredentials(credentials);
        }
    }

    private boolean validateInputs() {
        boolean isValid = true;

        if (mode == SigningMode.SAVED_KEY_STORE && selectedKeystore == null) {
            SketchwareUtil.toastError("No saved keystore selected. Import one from Settings → Keystore manager.");
            return false;
        }

        String keystorePath = Helper.getText(binding.etKeystorePath);
        if (TextUtils.isEmpty(keystorePath)) {
            binding.tilKeystorePath.setError("Keystore path can't be empty");
            isValid = false;
        } else if (!new File(keystorePath).exists()) {
            // Was a toast before, which closed the dialog together with the entered values.
            binding.tilKeystorePath.setError("Keystore not found at that path");
            isValid = false;
        } else {
            binding.tilKeystorePath.setError(null);
        }

        if (TextUtils.isEmpty(binding.etStorePassword.getText())) {
            binding.tilStorePassword.setError("Keystore password can't be empty");
            isValid = false;
        } else {
            binding.tilStorePassword.setError(null);
        }

        if (TextUtils.isEmpty(binding.etAlias.getText())) {
            binding.tilAlias.setError("Alias can't be empty");
            isValid = false;
        } else {
            binding.tilAlias.setError(null);
        }

        if (TextUtils.isEmpty(binding.etPassword.getText())) {
            binding.tilPassword.setError("Alias password can't be empty");
            isValid = false;
        } else {
            binding.tilPassword.setError(null);
        }

        if (TextUtils.isEmpty(binding.etSigningAlgorithm.getText())) {
            binding.tilSigningAlgorithm.setError("Algorithm can't be empty");
            isValid = false;
        } else {
            binding.tilSigningAlgorithm.setError(null);
        }

        return isValid;
    }

    public void show() {
        AlertDialog alertDialog = dialog.show();
        // AlertDialog dismisses itself on a positive button click; validating first needs the
        // listener to be replaced after the dialog is shown (otherwise wrong input closes the flow).
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> onNextButtonClick(alertDialog));

        // The dialog scrolls itself to whatever field got focus; without this the first thing the
        // user sees would be the last field, not "what to build". The soft keyboard is only worth
        // opening when something actually has to be typed.
        View root = binding.getRoot();
        root.post(() -> {
            if (root instanceof ScrollView) {
                ScrollView scrollView = (ScrollView) root;
                // The dialog's custom view is measured unbounded, so a form this long used to push
                // the dialog's own buttons (NEXT) out of the window. Give the form a share of the
                // screen and let it scroll instead.
                ViewGroup.LayoutParams params = scrollView.getLayoutParams();
                params.height = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.42f);
                scrollView.setLayoutParams(params);
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_UP));
            }
        });
        if (mode == SigningMode.CUSTOM_KEY_STORE && !isDebugFormat()) {
            binding.etAlias.requestFocus();
        }
    }

    public void setListener(CredentialsReceiver receiver) {
        this.receiver = receiver;
    }

    /**
     * Registers a listener that also receives what to build. Takes precedence over
     * {@link #setListener(CredentialsReceiver)} when both are set.
     */
    public void setCompileListener(CompileRequestReceiver compileReceiver) {
        this.compileReceiver = compileReceiver;
    }

    public enum SigningMode {
        SAVED_KEY_STORE("Sign using saved keystore"),
        CUSTOM_KEY_STORE("Sign using a keystore file"),
        TESTKEY("Sign using a test key"),
        DONT_SIGN("Don't sign");

        private final String label;

        SigningMode(String label) {
            this.label = label;
        }
    }

    /**
     * What the user asked to compile: the format plus the signing credentials.
     */
    public static class CompileRequest {

        private final Format format;
        private final Credentials credentials;

        public CompileRequest(Format format, Credentials credentials) {
            this.format = format;
            this.credentials = credentials;
        }

        /**
         * @return what to build. {@link Format#APK_RELEASE} when no format selector was shown.
         */
        public Format getFormat() {
            return format != null ? format : Format.APK_RELEASE;
        }

        /**
         * @return how to sign the result, or {@code null} to leave it unsigned.
         */
        public Credentials getCredentials() {
            return credentials;
        }

        public boolean isDebugApk() {
            return getFormat() == Format.APK_DEBUG;
        }

        public boolean isAppBundle() {
            return getFormat() == Format.AAB;
        }
    }

    public interface CompileRequestReceiver {
        void onCompile(CompileRequest request);
    }

    public interface CredentialsReceiver {
        /**
         * @param credentials The {@link Credentials} object made from user input.
         *                    May be null. In that case, the user disabled signing the file.
         */
        void gotCredentials(Credentials credentials);
    }

    public static class Credentials {

        private final boolean signWithTestkey;
        private final String keyStorePath;
        private final String keyStorePassword;
        private final String keyAlias;
        private final String keyPassword;
        private final String signingAlgorithm;

        /**
         * Constructs a credentials holder configured to sign with testkey,
         * meaning that no key store, aliases, and passwords were entered.
         */
        public Credentials(String signingAlgorithm) {
            signWithTestkey = true;
            keyStorePath = null;
            keyStorePassword = null;
            keyAlias = null;
            keyPassword = null;
            this.signingAlgorithm = signingAlgorithm;
        }

        /**
         * Constructs a credentials holder configured to sign with a private key taken from a key store.
         */
        public Credentials(String keyStorePath, String keyStorePassword, String keyAlias, String keyPassword, String signingAlgorithm) {
            signWithTestkey = false;
            this.keyStorePath = keyStorePath;
            this.keyStorePassword = keyStorePassword;
            this.keyAlias = keyAlias;
            this.keyPassword = keyPassword;
            this.signingAlgorithm = signingAlgorithm;
        }

        /**
         * @return False if this credentials holder contains credentials for signing, true if not.
         */
        public boolean isForSigningWithTestkey() {
            return signWithTestkey;
        }

        /**
         * @return The path of the keystore to sign with. {@code null} when signing with testkey.
         */
        public String getKeyStorePath() {
            return keyStorePath;
        }

        /**
         * @return {@link #keyStorePassword}
         */
        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        /**
         * @return {@link #keyAlias}
         */
        public String getKeyAlias() {
            return keyAlias;
        }

        /**
         * @return {@link #keyPassword}
         */
        public String getKeyPassword() {
            return keyPassword;
        }

        /**
         * @return {@link #signingAlgorithm}
         */
        public String getSigningAlgorithm() {
            return signingAlgorithm;
        }
    }
}
