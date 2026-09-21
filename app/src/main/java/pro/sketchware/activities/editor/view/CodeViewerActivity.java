package pro.sketchware.activities.editor.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import a.a.a.Lx;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityCodeViewerBinding;
import pro.sketchware.featureflags.FeatureFlags;
import pro.sketchware.lsp.LspClientFactory;
import pro.sketchware.lsp.LspDiagnosticsSnapshot;
import pro.sketchware.lsp.LspDiagnosticsStream;
import pro.sketchware.lsp.LspDocumentSession;
import pro.sketchware.lsp.LspLanguage;
import pro.sketchware.lsp.LspSessionConfig;
import pro.sketchware.metrics.EditorPerformanceMetricsStore;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class CodeViewerActivity extends BaseAppCompatActivity {

    public static final String SCHEME_XML = "xml";
    public static final String SCHEME_JAVA = "java";

    private static final int MENU_EDIT = 1;
    private static final int MENU_SAVE = 2;

    private ActivityCodeViewerBinding binding;
    private String scId;
    private String sourceFile;
    private String originalCode = "";
    private boolean editableMode;
    private boolean allowEditing;
    private String subtitleBase = "";
    private int lastDiagnosticsCount = 0;
    private LspDocumentSession lspSession;
    private SubscriptionReceipt<ContentChangeEvent> contentChangeSubscription;

    private final LspDiagnosticsStream.Listener diagnosticsListener = snapshot ->
            runOnUiThread(() -> applyDiagnosticsSnapshot(snapshot));

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityCodeViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        var code = getIntent().getStringExtra("code");
        var scheme = getIntent().getStringExtra("scheme");
        var title = getIntent().getStringExtra("title");
        scId = getIntent().getStringExtra("sc_id");
        sourceFile = getIntent().getStringExtra("source_file");
        allowEditing = FeatureFlags.isEnabled(this, FeatureFlags.Key.CODE_VIEWER_EDIT_AND_SAVE);

        binding.toolbar.setNavigationOnClickListener(v -> handleBackPressed());
        if (title != null && !title.isEmpty()) {
            binding.toolbar.setTitle(title);
        }
        setupToolbarActions();
        subtitleBase = scId == null ? "" : scId;
        updateSubtitle();

        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(14);
        originalCode = loadInitialCode(code);
        binding.editor.setText(originalCode);
        binding.editor.setEditable(false);
        binding.editor.setWordwrap(false);
        loadColorScheme(scheme);
        initializeLspSession(scheme, originalCode);

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
    }

    private void setupToolbarActions() {
        Menu menu = binding.toolbar.getMenu();
        menu.clear();
        if (!allowEditing) {
            return;
        }
        menu.add(Menu.NONE, MENU_EDIT, Menu.NONE, getString(R.string.common_word_edit))
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_edit))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_SAVE, Menu.NONE, getString(R.string.common_word_save))
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_save))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == MENU_EDIT) {
                toggleEditMode();
                return true;
            }
            if (id == MENU_SAVE) {
                saveCode();
                return true;
            }
            return false;
        });
    }

    private void toggleEditMode() {
        if (!allowEditing) {
            return;
        }
        editableMode = !editableMode;
        binding.editor.setEditable(editableMode);
        SketchwareUtil.toast(editableMode ? "Edit mode enabled" : "Edit mode disabled");
    }

    private void saveCode() {
        if (!allowEditing) {
            return;
        }
        String currentCode = binding.editor.getText().toString();
        if (currentCode.equals(originalCode)) {
            SketchwareUtil.toast("No changes to save");
            return;
        }
        String targetPath = resolveTargetPath();
        if (targetPath == null || targetPath.isEmpty()) {
            SketchwareUtil.toastError("This source cannot be saved from Code Viewer.");
            return;
        }

        File parentFile = new File(targetPath).getParentFile();
        if (parentFile != null) {
            FileUtil.makeDir(parentFile.getAbsolutePath());
        }
        FileUtil.writeFile(targetPath, currentCode);
        originalCode = currentCode;
        if (lspSession != null) {
            lspSession.updateDocument(currentCode);
        }

        Intent result = new Intent();
        result.putExtra("source_file", sourceFile);
        result.putExtra("code", currentCode);
        setResult(RESULT_OK, result);
        SketchwareUtil.toast("Saved");
    }

    private String resolveTargetPath() {
        if (scId == null || scId.isEmpty() || sourceFile == null || sourceFile.isEmpty()) {
            return null;
        }
        String basePath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/files/";
        if (sourceFile.endsWith(".java")) {
            return basePath + "java/" + sourceFile;
        }
        if (sourceFile.endsWith(".xml")) {
            if ("strings.xml".equals(sourceFile) || "colors.xml".equals(sourceFile) || "styles.xml".equals(sourceFile)) {
                return basePath + "resource/values/" + sourceFile;
            }
            if ("AndroidManifest.xml".equals(sourceFile)) {
                return null;
            }
            return basePath + "resource/layout/" + sourceFile;
        }
        return null;
    }

    private String loadInitialCode(String fallbackCode) {
        String fallback = Lx.j(fallbackCode, false);
        String targetPath = resolveTargetPath();
        if (targetPath == null || targetPath.isEmpty() || !FileUtil.isExistFile(targetPath)) {
            return fallback;
        }
        String savedCode = FileUtil.readFile(targetPath);
        if (savedCode == null || savedCode.isEmpty()) {
            return fallback;
        }
        return Lx.j(savedCode, false);
    }

    private void handleBackPressed() {
        if (!hasUnsavedChanges()) {
            super.onBackPressed();
            finish();
            return;
        }
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.ic_warning_96dp);
        dialog.setTitle(Helper.getResString(R.string.common_word_warning));
        dialog.setMessage(Helper.getResString(R.string.src_code_editor_unsaved_changes_dialog_warning_message));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_exit), (v, which) -> finish());
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private boolean hasUnsavedChanges() {
        return !originalCode.equals(binding.editor.getText().toString());
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
    }

    private void loadColorScheme(String scheme) {
        if (SCHEME_XML.equals(scheme)) {
            EditorUtils.loadXmlConfig(binding.editor);
        } else {
            EditorUtils.loadJavaConfig(binding.editor);
        }
    }

    private void initializeLspSession(String scheme, String initialText) {
        if (!FeatureFlags.isEnabled(this, FeatureFlags.Key.EDITOR_LSP_ABSTRACTION)) {
            return;
        }

        LspLanguage language = LspLanguage.fromScheme(scheme);
        lspSession = LspClientFactory.createDefault(getApplicationContext()).createSession(
                new LspSessionConfig(scId, sourceFile, language)
        );
        lspSession.diagnostics().addListener(diagnosticsListener);
        lspSession.openDocument(initialText);
        contentChangeSubscription = binding.editor.subscribeEvent(ContentChangeEvent.class,
                (event, unsubscribe) -> {
                    if (lspSession != null) {
                        lspSession.updateDocument(binding.editor.getText().toString());
                    }
                });
    }

    private void applyDiagnosticsSnapshot(LspDiagnosticsSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        lastDiagnosticsCount = snapshot.diagnostics.size();
        long renderStartedAt = System.currentTimeMillis();
        EditorDiagnosticsGutterRenderer.render(binding.editor, snapshot);
        long renderDurationMs = Math.max(0L, System.currentTimeMillis() - renderStartedAt);
        EditorPerformanceMetricsStore.recordSample(
                getApplicationContext(),
                EditorPerformanceMetricsStore.OP_DIAGNOSTICS_RENDER,
                renderDurationMs
        );
        long publishToRenderMs = Math.max(0L, renderStartedAt - snapshot.timestampMs);
        EditorPerformanceMetricsStore.recordSample(
                getApplicationContext(),
                EditorPerformanceMetricsStore.OP_DIAGNOSTICS_PUBLISH_TO_RENDER,
                publishToRenderMs
        );
        updateSubtitle();
    }

    private void updateSubtitle() {
        String subtitle = subtitleBase;
        if (lastDiagnosticsCount > 0) {
            subtitle = subtitle + " | Diag: " + lastDiagnosticsCount;
        }
        binding.toolbar.setSubtitle(subtitle);
    }

    @Override
    public void onDestroy() {
        if (contentChangeSubscription != null) {
            contentChangeSubscription.unsubscribe();
            contentChangeSubscription = null;
        }
        if (lspSession != null) {
            lspSession.diagnostics().removeListener(diagnosticsListener);
            lspSession.closeDocument();
            lspSession.dispose();
            lspSession = null;
        }
        super.onDestroy();
    }
}