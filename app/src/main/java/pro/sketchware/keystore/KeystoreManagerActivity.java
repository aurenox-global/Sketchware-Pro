package pro.sketchware.keystore;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityKeystoreManagerBinding;
import pro.sketchware.databinding.DialogImportKeystoreBinding;
import pro.sketchware.databinding.ItemKeystoreBinding;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Settings → Keystore manager. Imports .jks/.keystore/.bks/.p12 files into the app's private
 * storage and remembers their alias/passwords (encrypted, {@link KeystoreStore}).
 */
public class KeystoreManagerActivity extends BaseAppCompatActivity {

    private ActivityKeystoreManagerBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityKeystoreManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.topAppBar.setTitle("Keystore manager");
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        binding.importKeystore.setOnClickListener(v -> pickKeystoreFile());

        refreshList();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            refreshList();
        }
    }

    private void refreshList() {
        binding.keystoreContainer.removeAllViews();
        List<KeystoreStore.Entry> entries = KeystoreStore.list(this);

        binding.emptyHint.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (KeystoreStore.Entry entry : entries) {
            ItemKeystoreBinding item = ItemKeystoreBinding.inflate(inflater, binding.keystoreContainer, false);
            item.keystoreName.setText(entry.getName());
            item.keystoreAlias.setText("Alias: " + entry.getAlias());
            String fingerprint = KeystoreStore.certificateSha256(entry, this);
            item.keystoreFingerprint.setText("SHA-256: " + fingerprint);
            item.getRoot().setOnClickListener(v -> showEntryOptions(entry));
            binding.keystoreContainer.addView(item.getRoot());
        }
    }

    private void showEntryOptions(KeystoreStore.Entry entry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(entry.getName())
                .setItems(new String[]{"Show certificate", "Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        showCertificate(entry);
                    } else {
                        confirmDelete(entry);
                    }
                })
                .show();
    }

    private void showCertificate(KeystoreStore.Entry entry) {
        String sha256 = KeystoreStore.certificateSha256(entry, this);
        String subject = KeystoreStore.certificateSubject(entry, this);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Certificate")
                .setMessage("Alias: " + entry.getAlias()
                        + "\nSubject: " + subject
                        + "\nSHA-256: " + sha256
                        + "\nFile: " + entry.file(this).getAbsolutePath())
                .setPositiveButton("Close", null)
                .show();
    }

    private void confirmDelete(KeystoreStore.Entry entry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + entry.getName() + "?")
                .setMessage("The keystore file and its stored passwords will be removed from this device. This cannot be undone.")
                .setPositiveButton(R.string.common_word_delete, (dialog, which) -> {
                    KeystoreStore.delete(this, entry.getId());
                    refreshList();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void pickKeystoreFile() {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"jks", "keystore", "bks", "p12", "pfx"});
        options.setTitle("Select a keystore");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                showImportDialog(file);
            }
        };

        new FilePickerDialogFragment(options, callback).show(getSupportFragmentManager(), "keystore_picker");
    }

    private void showImportDialog(File file) {
        DialogImportKeystoreBinding dialogBinding = DialogImportKeystoreBinding.inflate(getLayoutInflater());
        dialogBinding.etName.setText(file.getName());
        dialogBinding.etAlgorithm.setText("SHA256withRSA");

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Import " + file.getName())
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton("Import", null);

        androidx.appcompat.app.AlertDialog alertDialog = dialog.create();
        alertDialog.show();
        // Validate first: on failure the dialog must stay open so the user can fix the input.
        alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = Helper.getText(dialogBinding.etName);
            String alias = Helper.getText(dialogBinding.etAlias);
            String storePassword = Helper.getText(dialogBinding.etStorePassword);
            String keyPassword = Helper.getText(dialogBinding.etKeyPassword);
            String algorithm = Helper.getText(dialogBinding.etAlgorithm);

            if (TextUtils.isEmpty(name)) {
                dialogBinding.tilName.setError("Name can't be empty");
                return;
            }
            if (TextUtils.isEmpty(alias)) {
                dialogBinding.tilAlias.setError("Alias can't be empty");
                return;
            }
            if (TextUtils.isEmpty(storePassword)) {
                dialogBinding.tilStorePassword.setError("Password can't be empty");
                return;
            }
            if (TextUtils.isEmpty(keyPassword)) {
                dialogBinding.tilKeyPassword.setError("Password can't be empty");
                return;
            }

            KeystoreStore.Entry entry = KeystoreStore.importKeystore(this, name, alias, algorithm,
                    storePassword, keyPassword, file);
            if (entry == null) {
                SketchwareUtil.toastError("Could not copy the keystore file");
                return;
            }

            String result = KeystoreStore.certificateSha256(entry, this);
            if (result.contains(":")) {
                alertDialog.dismiss();
                SketchwareUtil.toast("Keystore imported");
                refreshList();
            } else {
                // Credentials are wrong: don't keep a broken entry around.
                KeystoreStore.delete(this, entry.getId());
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Could not read the keystore")
                        .setMessage(result)
                        .setPositiveButton("Okay", null)
                        .show();
            }
        });
    }
}
