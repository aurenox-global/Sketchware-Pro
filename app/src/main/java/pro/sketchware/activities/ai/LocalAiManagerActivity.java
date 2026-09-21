package pro.sketchware.activities.ai;

import android.app.Activity;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.ai.AiProviderCatalog;
import pro.sketchware.ai.CloudAiService;
import pro.sketchware.ai.LocalAiBridge;
import pro.sketchware.ai.LocalAiConfig;
import pro.sketchware.ai.LocalAiException;
import pro.sketchware.ai.LocalAiModelCatalog;
import pro.sketchware.ai.LocalAiModelDownloader;
import pro.sketchware.ai.LocalAiModelInfo;
import pro.sketchware.ai.LocalAiService;
import pro.sketchware.databinding.ActivityLocalAiManagerBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class LocalAiManagerActivity extends BaseAppCompatActivity {
    private static final int REQUEST_PICK_GGUF_MODEL = 7201;
    private static final String CHECK_STATUS_UNKNOWN = "unknown";
    private static final String CHECK_STATUS_OK = "ok";
    private static final String CHECK_STATUS_ERROR = "error";
    private static final String PREF_LAST_CHECK_STATUS = "last_check_status";
    private static final String PREF_LAST_CHECK_PROVIDER = "last_check_provider";
    private static final String PREF_LAST_CHECK_TIME_MS = "last_check_time_ms";
    private static final String PREF_LAST_CHECK_MESSAGE = "last_check_message";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private ActivityLocalAiManagerBinding binding;
    private boolean runningTest;
    private boolean applyingSourceSelection;
    private final Map<String, CatalogRow> catalogRows = new HashMap<>();
    private LocalAiModelDownloader activeDownloader;
    private String activeDownloadFamilyId;
    private boolean applyingCatalogStates;

    private void setupTabs() {
        if (binding.managerTabs.getTabCount() == 0) {
            binding.managerTabs.addTab(binding.managerTabs.newTab().setText("Local Model"));
            binding.managerTabs.addTab(binding.managerTabs.newTab().setText("Model Catalog"));
            binding.managerTabs.addTab(binding.managerTabs.newTab().setText("Engine"));
        }
        binding.managerTabs.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                updateTabPanels(tab.getPosition());
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }
        });
        binding.managerTabs.selectTab(binding.managerTabs.getTabAt(0));
        updateTabPanels(0);
    }

    private void updateTabPanels(int position) {
        binding.tabLocalPanel.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        binding.tabCatalogPanel.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        binding.tabEnginePanel.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
    }

    private void setupEngineSliders() {
        binding.contextSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            binding.contextSizeLabel.setText("Context: " + (int) value + " / max " + (int) slider.getValueTo());
        });
        binding.threadsSlider.addOnChangeListener((slider, value, fromUser) -> {
            binding.threadsLabel.setText("Threads: " + (int) value);
        });
        binding.maxTokensSlider.addOnChangeListener((slider, value, fromUser) -> {
            binding.maxTokensLabel.setText("Max tokens: " + (int) value);
        });
        binding.temperatureSlider.addOnChangeListener((slider, value, fromUser) -> {
            binding.temperatureLabel.setText("Temperature: " + String.format(Locale.US, "%.2f", value));
        });
        binding.topPSlider.addOnChangeListener((slider, value, fromUser) -> {
            binding.topPLabel.setText("Top P: " + String.format(Locale.US, "%.2f", value));
        });
        binding.topKSlider.addOnChangeListener((slider, value, fromUser) -> {
            binding.topKLabel.setText("Top K: " + (int) value);
        });
        binding.presencePenaltySlider.addOnChangeListener((slider, value, fromUser) -> {
            binding.presencePenaltyLabel.setText("Presence penalty: " + String.format(Locale.US, "%.2f", value));
        });
    }

    private void updateSliderRanges() {
        LocalAiConfig config = LocalAiConfig.load(this);
        int maxContext = 32768;
        if (!config.getModelPath().isEmpty()) {
            try {
                LocalAiModelInfo info = LocalAiModelInfo.fromPath(config.getModelPath());
                maxContext = (int) info.getMaxContextForDevice(getDeviceBudgetBytes());
            } catch (LocalAiException ignored) {
                android.util.Log.d("SketchwarePro", "LocalAiManagerActivity: LocalAiException ignored", ignored);
            }
        }
        maxContext = Math.max(128, Math.min(LocalAiConfig.MAX_CONTEXT_SIZE, maxContext));
        binding.contextSizeSlider.setValueTo(maxContext);
        binding.maxTokensSlider.setValueTo(maxContext);
        binding.contextSizeLabel.setText("Context: " + (int) binding.contextSizeSlider.getValue() + " / max " + maxContext);
    }

    private long getDeviceBudgetBytes() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo memoryInfo = new android.app.ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(memoryInfo);
            }
            // Weights are mmap'd (page cache, evictable), so the real budget is
            // available RAM minus the model file size minus ~1 GB of app overhead.
            long budget = memoryInfo.availMem - (1024L * 1024L * 1024L);
            LocalAiConfig config = LocalAiConfig.load(this);
            if (!config.getModelPath().isEmpty()) {
                File modelFile = new File(config.getModelPath());
                if (modelFile.isFile()) {
                    budget -= modelFile.length();
                }
            }
            return Math.max(0L, budget);
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
        binding = ActivityLocalAiManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        setupTabs();
        setupEngineSliders();
        setupSourceControls();
        setupActions();
        setupThinkModeToggle();
        setupModelCatalog();
        refreshUi();

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToPadding(binding.contentLayout, true, false, true, true);
    }

    @Override
    public void onDestroy() {
        LocalAiService.getInstance().cancel();
        CloudAiService.getInstance().cancel();
        if (activeDownloader != null) {
            activeDownloader.cancel();
            activeDownloader = null;
        }
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private void setupSourceControls() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                AiProviderCatalog.getCloudProviderLabelList()
        );
        binding.cloudProviderInput.setAdapter(adapter);
        binding.cloudProviderInput.setOnItemClickListener((parent, view, position, id) -> {
            String[] providerIds = AiProviderCatalog.getCloudProviderIds();
            if (position >= 0 && position < providerIds.length) {
                applyProviderDefaults(providerIds[position], false);
                updateSourceUiFromSelection();
                updateSourceSummaryText();
            }
        });

        binding.sourceLocalButton.setOnClickListener(v -> {
            binding.sourceToggleGroup.check(binding.sourceLocalButton.getId());
            updateSourceUiFromSelection();
            updateSourceSummaryText();
        });

        binding.sourceCloudButton.setOnClickListener(v -> {
            binding.sourceToggleGroup.check(binding.sourceCloudButton.getId());
            updateSourceUiFromSelection();
            updateSourceSummaryText();
        });

        binding.sourceToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || applyingSourceSelection) {
                return;
            }
            updateSourceUiFromSelection();
            updateSourceSummaryText();
        });

        if (binding.sourceToggleGroup.getCheckedButtonId() == View.NO_ID) {
            binding.sourceToggleGroup.check(binding.sourceLocalButton.getId());
            updateSourceUiFromSelection();
            updateSourceSummaryText();
        }
    }

    private void setupActions() {
        binding.importModelButton.setOnClickListener(v -> openModelPicker());
        binding.useModelButton.setOnClickListener(v -> useSelectedModel());
        binding.saveButton.setOnClickListener(v -> saveConfig(true));
        binding.checkConnectionButton.setOnClickListener(v -> runConnectivityCheck());
        binding.testButton.setOnClickListener(v -> runTestPrompt());
        binding.cancelButton.setOnClickListener(v -> {
            LocalAiService.getInstance().cancel();
            CloudAiService.getInstance().cancel();
            SketchwareUtil.toast("Cancelling AI request...");
        });
    }

    private void setupThinkModeToggle() {
        LocalAiConfig config = LocalAiConfig.load(this);
        binding.thinkModeSwitch.setChecked(config.isThinkMode());
        binding.thinkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            applyQwenSamplingPreset(isChecked);
            LocalAiConfig current = LocalAiConfig.load(this);
            current.setThinkMode(isChecked);
            current.save(getApplicationContext());
            refreshUi();
        });
    }

    /**
     * Applies Unsloth's recommended Qwen3.5 sampling presets:
     * thinking mode -> temp 1.0 / top_p 0.95 (presence_penalty handled by config),
     * non-thinking  -> temp 0.5 / top_p 0.85 (deterministic instruction following).
     */
    private void applyQwenSamplingPreset(boolean thinking) {
        LocalAiConfig current = LocalAiConfig.load(this);
        if (thinking) {
            current.setTemperature(1.0f);
            current.setTopP(0.95f);
            binding.thinkModeSummary.setText("Razonamiento visible (Qwen3.5: temp 1.0, top_p 0.95)");
        } else {
            current.setTemperature(0.5f);
            current.setTopP(0.85f);
            binding.thinkModeSummary.setText("Razonamiento oculto (Qwen3.5: temp 0.5, top_p 0.85)");
        }
        current.save(getApplicationContext());
    }

    private void setupModelCatalog() {
        binding.catalogCancelButton.setOnClickListener(v -> cancelCatalogDownloads());
        LinearLayout catalogList = binding.catalogList;
        catalogList.removeAllViews();
        catalogRows.clear();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (LocalAiModelCatalog.ModelFamily family : LocalAiModelCatalog.getFamilies()) {
            View rowView = inflater.inflate(R.layout.item_ai_model_catalog, catalogList, false);
            CatalogRow row = new CatalogRow(family, rowView);
            row.familyNameText.setText(family.name);
            row.badgeText.setText("GGUF");
            row.familyDescText.setText(family.description + "  ·  ctx máx "
                    + formatContext(family.contextLength));
            if (family.quants.size() <= 1) {
                row.quantChipsRow.setVisibility(View.GONE);
            }

            buildQuantChips(row);
            selectQuant(row, defaultQuant(row.family));

            row.downloadButton.setOnClickListener(v -> onCatalogDownload(row));
            row.importModelButton.setOnClickListener(v -> openModelPicker());
            row.enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (applyingCatalogStates) {
                    return;
                }
                if (isChecked) {
                    activateCatalogModel(row);
                } else {
                    deactivateCatalogModel(row);
                }
            });
            row.deleteButton.setOnClickListener(v -> confirmDeleteCatalogModel(row));

            catalogList.addView(rowView);
            catalogRows.put(family.id, row);
            updateCatalogRowState(row);
        }
    }

    private static String formatContext(int contextLength) {
        if (contextLength <= 0) {
            return "?";
        }
        if (contextLength >= 1000) {
            return (contextLength / 1000) + "k";
        }
        return String.valueOf(contextLength);
    }

    private LocalAiModelCatalog.QuantEntry defaultQuant(LocalAiModelCatalog.ModelFamily family) {
        for (LocalAiModelCatalog.QuantEntry quant : family.quants) {
            if (quant.recommended) {
                return quant;
            }
        }
        return family.quants.isEmpty() ? null : family.quants.get(0);
    }

    private void buildQuantChips(CatalogRow row) {
        LinearLayout chipsRow = row.quantChipsRow;
        chipsRow.removeAllViews();
        for (LocalAiModelCatalog.QuantEntry quant : row.family.quants) {
            MaterialButton chip = new MaterialButton(this);
            chip.setCheckable(true);
            chip.setChecked(false);
            chip.setAllCaps(false);
            chip.setTextSize(11);
            chip.setText(quantLabel(quant));
            chip.setMinWidth(0);
            chip.setMinHeight((int) dp(34));
            chip.setMaxWidth((int) dp(120));
            chip.setPadding((int) dp(12), 0, (int) dp(12), 0);
            chip.setInsetTop(0);
            chip.setInsetBottom(0);
            chip.setCornerRadius((int) dp(17));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipParams.setMargins(0, 0, (int) dp(6), 0);
            chip.setLayoutParams(chipParams);
            chip.setOnClickListener(v -> selectQuant(row, quant));
            chipsRow.addView(chip);
            row.quantChips.put(quant.fileName, chip);
        }
    }

    private static String quantLabel(LocalAiModelCatalog.QuantEntry quant) {
        String label = quant.fileName
                .replaceFirst("^.*?-(Q\\d+_\\d+)\\.gguf$", "$1")
                .replaceFirst("^.*?-(UD-Q\\d+_\\d+)\\.gguf$", "$1")
                .replaceFirst("\\.gguf$", "");
        return label + (quant.recommended ? " ★" : "");
    }

    private void selectQuant(CatalogRow row, LocalAiModelCatalog.QuantEntry quant) {
        if (quant == null) {
            return;
        }
        row.selectedQuant = quant;
        for (Map.Entry<String, MaterialButton> entry : row.quantChips.entrySet()) {
            boolean isSelected = entry.getKey().equals(quant.fileName);
            MaterialButton chip = entry.getValue();
            chip.setChecked(isSelected);
            if (isSelected) {
                chip.setBackgroundTintList(ColorStateList.valueOf(MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorPrimaryContainer)));
                chip.setTextColor(MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnPrimaryContainer));
                chip.setStrokeColor(ColorStateList.valueOf(MaterialColors.getColor(binding.getRoot(), android.R.attr.colorPrimary)));
            } else {
                chip.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
                chip.setTextColor(MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnSurfaceVariant));
                chip.setStrokeColor(ColorStateList.valueOf(MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOutlineVariant)));
            }
        }
    }

    private void onCatalogDownload(CatalogRow row) {
        if (activeDownloader != null) {
            SketchwareUtil.toast("A download is already in progress.");
            return;
        }
        if (runningTest || row.selectedQuant == null) {
            return;
        }
        if (catalogModelFile(row).isFile()) {
            activateCatalogModel(row);
            return;
        }
        startCatalogDownload(row);
    }

    private File catalogModelFile(CatalogRow row) {
        return new File(LocalAiConfig.getModelsDirectory(), row.selectedQuant.fileName);
    }

    private void startCatalogDownload(CatalogRow row) {
        final LocalAiModelCatalog.ModelFamily family = row.family;
        final LocalAiModelCatalog.QuantEntry quant = row.selectedQuant;
        activeDownloadFamilyId = family.id;
        activeDownloader = new LocalAiModelDownloader();
        binding.catalogCancelButton.setEnabled(true);
        setCatalogRowDownloading(row, true, "Preparing download... 0%");

        ioExecutor.execute(() -> {
            File modelFile = new File(LocalAiConfig.getModelsDirectory(), quant.fileName);
            activeDownloader.download(family.getDownloadUrl(quant.fileName), modelFile, new LocalAiModelDownloader.Callback() {
                @Override
                public void onProgress(long downloadedBytes, long totalBytes) {
                    runOnUiThread(() -> updateCatalogProgress(row, downloadedBytes, totalBytes));
                }

                @Override
                public void onSuccess(File downloadedFile) {
                    try {
                        LocalAiModelInfo.fromPath(downloadedFile.getAbsolutePath());
                    } catch (LocalAiException e) {
                        runOnUiThread(() -> finishCatalogDownload(row, false, "Invalid GGUF downloaded: " + e.getMessage()));
                        return;
                    }
                    runOnUiThread(() -> finishCatalogDownload(row, true, null));
                }

                @Override
                public void onError(Throwable throwable) {
                    runOnUiThread(() -> finishCatalogDownload(row, false, throwable.getMessage()));
                }
            });
        });
    }

    private void finishCatalogDownload(CatalogRow row, boolean success, String errorMessage) {
        activeDownloader = null;
        activeDownloadFamilyId = null;
        binding.catalogCancelButton.setEnabled(false);
        setCatalogRowDownloading(row, false, null);
        if (success) {
            SketchwareUtil.toast(row.selectedQuant.fileName + " descargado");
            activateCatalogModel(row);
        } else {
            SketchwareUtil.showAnErrorOccurredDialog(this, errorMessage == null ? "Download failed." : errorMessage);
            updateCatalogRowState(row);
        }
    }

    private void updateCatalogProgress(CatalogRow row, long downloadedBytes, long totalBytes) {
        int progress = totalBytes > 0L
                ? (int) Math.min(100L, (downloadedBytes * 100L) / totalBytes)
                : -1;
        row.progress.setIndeterminate(progress < 0);
        if (progress >= 0) {
            row.progress.setProgressCompat(progress, true);
        }
        String text = "Downloading... " + (progress >= 0
                ? progress + "% (" + pro.sketchware.utility.FileUtil.formatFileSize(downloadedBytes) + ")"
                : pro.sketchware.utility.FileUtil.formatFileSize(downloadedBytes));
        row.statusText.setText(text);
    }

    private void setCatalogRowDownloading(CatalogRow row, boolean downloading, String statusText) {
        row.progress.setVisibility(downloading ? View.VISIBLE : View.GONE);
        row.statusText.setVisibility(downloading ? View.VISIBLE : View.GONE);
        row.downloadButton.setEnabled(!downloading);
        row.enableSwitch.setEnabled(!downloading);
        row.deleteButton.setEnabled(!downloading);
        if (statusText != null) {
            row.statusText.setText(statusText);
        }
    }

    private void updateCatalogRowState(CatalogRow row) {
        if (row.selectedQuant == null) {
            return;
        }
        LocalAiConfig config = LocalAiConfig.load(this);
        File modelFile = catalogModelFile(row);
        boolean downloaded = modelFile.isFile();
        boolean active = downloaded && modelFile.getAbsolutePath().equals(config.getModelPath());
        boolean canDownload = activeDownloader == null && !runningTest;

        row.downloadButton.setIconResource(downloaded ? R.drawable.ic_mtrl_loaded : R.drawable.ic_mtrl_download);
        row.downloadButton.setText(downloaded ? "Descargado" : "Descargar");
        row.downloadButton.setEnabled(canDownload && !downloaded);

        applyingCatalogStates = true;
        row.enableSwitch.setChecked(active);
        applyingCatalogStates = false;
        row.enableSwitch.setEnabled(downloaded && canDownload);
        row.deleteButton.setEnabled(downloaded && canDownload);
    }

    private void updateAllCatalogRowStates() {
        for (CatalogRow row : catalogRows.values()) {
            updateCatalogRowState(row);
        }
    }

    private void cancelCatalogDownloads() {
        if (activeDownloader != null) {
            activeDownloader.cancel();
            activeDownloader = null;
            activeDownloadFamilyId = null;
        }
        binding.catalogCancelButton.setEnabled(false);
        for (CatalogRow row : catalogRows.values()) {
            setCatalogRowDownloading(row, false, null);
            updateCatalogRowState(row);
        }
        SketchwareUtil.toast("Download cancelled");
    }

    private void activateCatalogModel(CatalogRow row) {
        if (row.selectedQuant == null) {
            return;
        }
        LocalAiConfig config = LocalAiConfig.load(this);
        config.setModelPath(catalogModelFile(row).getAbsolutePath());
        config.save(getApplicationContext());
        SketchwareUtil.toast("Modelo activado: " + row.selectedQuant.fileName);
        refreshUi();
    }

    private void deactivateCatalogModel(CatalogRow row) {
        LocalAiConfig config = LocalAiConfig.load(this);
        File modelFile = catalogModelFile(row);
        if (modelFile.getAbsolutePath().equals(config.getModelPath())) {
            config.setModelPath("");
        }
        config.save(getApplicationContext());
        SketchwareUtil.toast("Modelo desactivado: " + row.family.name);
        refreshUi();
    }

    private void confirmDeleteCatalogModel(CatalogRow row) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Eliminar modelo")
                .setMessage("Se eliminarán los archivos de " + row.family.name + " de la tarjeta. ¿Continuar?")
                .setPositiveButton("Eliminar", (dialog, which) -> deleteCatalogModel(row))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteCatalogModel(CatalogRow row) {
        LocalAiConfig config = LocalAiConfig.load(this);
        File modelFile = catalogModelFile(row);
        boolean wasActive = modelFile.getAbsolutePath().equals(config.getModelPath());

        if (modelFile.isFile()) {
            FileUtil.deleteFile(modelFile.getAbsolutePath());
        }

        if (wasActive) {
            config.setModelPath("");
            config.save(getApplicationContext());
        }
        SketchwareUtil.toast("Modelo eliminado: " + row.family.name);
        refreshUi();
    }

    private static final class CatalogRow {
        private final LocalAiModelCatalog.ModelFamily family;
        private LocalAiModelCatalog.QuantEntry selectedQuant;
        private final TextView familyNameText;
        private final TextView familyDescText;
        private final TextView badgeText;
        private final TextView statusText;
        private final LinearLayout quantChipsRow;
        private final MaterialButton downloadButton;
        private final MaterialButton importModelButton;
        private final com.google.android.material.materialswitch.MaterialSwitch enableSwitch;
        private final MaterialButton deleteButton;
        private final LinearProgressIndicator progress;
        private final Map<String, MaterialButton> quantChips = new HashMap<>();

        private CatalogRow(LocalAiModelCatalog.ModelFamily family, View rowView) {
            this.family = family;
            familyNameText = rowView.findViewById(R.id.catalog_family_name);
            familyDescText = rowView.findViewById(R.id.catalog_family_desc);
            badgeText = rowView.findViewById(R.id.catalog_badge);
            statusText = rowView.findViewById(R.id.catalog_status_text);
            quantChipsRow = rowView.findViewById(R.id.quant_chips_row);
            downloadButton = rowView.findViewById(R.id.catalog_download_button);
            importModelButton = rowView.findViewById(R.id.catalog_import_model_button);
            enableSwitch = rowView.findViewById(R.id.catalog_enable_switch);
            deleteButton = rowView.findViewById(R.id.catalog_delete_button);
            progress = rowView.findViewById(R.id.catalog_progress);
        }
    }

    private void openModelPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select a GGUF model"), REQUEST_PICK_GGUF_MODEL);
        } catch (ActivityNotFoundException e) {
            SketchwareUtil.showAnErrorOccurredDialog(this, "No file picker app is available on this device.");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }

        if (requestCode != REQUEST_PICK_GGUF_MODEL) {
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                Uri clipUri = data.getClipData().getItemAt(i).getUri();
                if (clipUri != null && !uris.contains(clipUri)) {
                    uris.add(clipUri);
                }
            }
        }
        if (!uris.isEmpty()) {
            int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            for (Uri uri : uris) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                } catch (RuntimeException ignored) {
                    android.util.Log.d("SketchwarePro", "LocalAiManagerActivity: RuntimeException ignored", ignored);
                }
            }
            importModelFiles(uris);
        }
    }

    private void importModelFiles(ArrayList<Uri> uris) {
        setImportProgress(0, "Importing model... 0%");
        ioExecutor.execute(() -> {
            final File[] importedModelHolder = {null};
            try {
                for (Uri uri : uris) {
                    if (importedModelHolder[0] == null) {
                        importedModelHolder[0] = copyUriToModels(uri, "Importing model...");
                        LocalAiModelInfo.fromPath(importedModelHolder[0].getAbsolutePath());
                    }
                }

                if (importedModelHolder[0] == null) {
                    throw new LocalAiException("No GGUF model file found in the selection.");
                }

                File importedModel = importedModelHolder[0];
                LocalAiConfig config = readConfigFromFields();
                config.setModelPath(importedModel.getAbsolutePath());
                config.save(getApplicationContext());
                runOnUiThread(() -> {
                    SketchwareUtil.toast("Model imported and selected");
                    refreshUi();
                    binding.resultText.setText("Model selected. Tap Use to load it in RAM.\n"
                            + importedModel.getAbsolutePath());
                });
            } catch (LocalAiException | RuntimeException e) {
                runOnUiThread(() -> SketchwareUtil.showAnErrorOccurredDialog(this, e.getMessage()));
            } finally {
                runOnUiThread(() -> setBusy(false, null));
            }
        });
    }

    private File copyUriToModels(Uri uri, String statusPrefix) {
        final int[] lastProgress = {-1};
        try {
            return LocalAiConfig.copyModelToModelsDirectory(getApplicationContext(), uri, (copiedBytes, totalBytes) -> {
                if (totalBytes > 0L) {
                    int progress = (int) Math.min(100L, (copiedBytes * 100L) / Math.max(1L, totalBytes));
                    if (progress != lastProgress[0]) {
                        lastProgress[0] = progress;
                        runOnUiThread(() -> setImportProgress(progress, statusPrefix + progress + "%"));
                    }
                } else {
                    runOnUiThread(() -> setImportProgress(-1, statusPrefix + " " + pro.sketchware.utility.FileUtil.formatFileSize(copiedBytes)));
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getUriDisplayName(Context context, Uri sourceUri) {
        try (android.database.Cursor cursor = context.getContentResolver().query(sourceUri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (columnIndex >= 0) {
                    String displayName = cursor.getString(columnIndex);
                    if (displayName != null && !displayName.isEmpty()) {
                        return displayName;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            android.util.Log.d("SketchwarePro", "LocalAiManagerActivity: RuntimeException ignored", ignored);
        }
        String path = sourceUri.getLastPathSegment();
        if (path == null) {
            return "";
        }
        int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf(':'));
        return separator >= 0 ? path.substring(separator + 1) : path;
    }

    private void refreshUi() {
        LocalAiConfig config = LocalAiConfig.load(this);

        binding.modelPathText.setText(config.getModelPath().isEmpty() ? "No model selected" : config.getModelPath());
        updateSliderRanges();
        binding.contextSizeSlider.setValue(clamp(config.getContextSize(), 128, (int) binding.contextSizeSlider.getValueTo()));
        binding.threadsSlider.setValue(clamp(config.getThreads(), 1, LocalAiConfig.MAX_THREADS));
        binding.threadsLabel.setText("Threads: " + (int) binding.threadsSlider.getValue());
        binding.maxTokensSlider.setValue(clamp(config.getMaxTokens(), 1, (int) binding.maxTokensSlider.getValueTo()));
        binding.maxTokensLabel.setText("Max tokens: " + (int) binding.maxTokensSlider.getValue());
        binding.temperatureSlider.setValue(clamp(config.getTemperature(), 0f, LocalAiConfig.MAX_TEMPERATURE));
        binding.temperatureLabel.setText("Temperature: " + String.format(Locale.US, "%.2f", binding.temperatureSlider.getValue()));
        binding.topPSlider.setValue(clamp(config.getTopP(), 0.01f, 1f));
        binding.topPLabel.setText("Top P: " + String.format(Locale.US, "%.2f", binding.topPSlider.getValue()));
        binding.topKSlider.setValue(clamp(config.getTopK(), 1, 100));
        binding.topKLabel.setText("Top K: " + (int) binding.topKSlider.getValue());
        binding.presencePenaltySlider.setValue(clamp(config.getPresencePenalty(), 0f, 2f));
        binding.presencePenaltyLabel.setText("Presence penalty: " + String.format(Locale.US, "%.2f", binding.presencePenaltySlider.getValue()));
        binding.thinkModeSwitch.setChecked(config.isThinkMode());
        binding.thinkModeSummary.setText(config.isThinkMode()
                ? "Razonamiento visible (Qwen3.5: temp 1.0, top_p 0.95)"
                : "Razonamiento oculto (Qwen3.5: temp 0.5, top_p 0.85)");

        String cloudProvider = config.isCloudProvider() ? config.getProviderId() : LocalAiConfig.PROVIDER_DEEPSEEK;
        String cloudProviderLabel = AiProviderCatalog.getProviderLabel(cloudProvider);
        binding.cloudProviderInput.setText(cloudProviderLabel, false);
        binding.cloudApiKeyInput.setText(config.getCloudApiKey());
        binding.cloudModelInput.setText(config.resolveCloudModel());
        binding.cloudEndpointInput.setText(config.resolveCloudEndpoint());

        applyingSourceSelection = true;
        if (config.isCloudProvider()) {
            binding.sourceToggleGroup.check(binding.sourceCloudButton.getId());
        } else {
            binding.sourceToggleGroup.check(binding.sourceLocalButton.getId());
        }
        applyingSourceSelection = false;

        updateSourceUiFromSelection();

        if (config.isCloudProvider()) {
            String providerName = LocalAiConfig.getProviderDisplayName(config.getProviderId());
            binding.modelInfoText.setText("Cloud provider: " + providerName
                    + "\nModel: " + config.resolveCloudModel()
                    + "\nEndpoint: " + config.resolveCloudEndpoint()
                    + "\nAPI key: " + config.getMaskedCloudApiKey());
            binding.nativeStatusText.setText("Cloud mode active. Local native runtime is not required for this source.");
        } else {
            binding.nativeStatusText.setText(LocalAiBridge.getNativeStatus());
            try {
                LocalAiModelInfo modelInfo = LocalAiModelInfo.fromPath(config.getModelPath());
                binding.modelInfoText.setText(modelInfo.getDisplaySummary()
                        + "\n" + LocalAiService.getInstance().getLoadedModelStatus(this));
            } catch (LocalAiException e) {
                binding.modelInfoText.setText("Select a valid Q4/Q5/Q8 .gguf model. Q4 models are recommended for phones.");
            }
        }

        updateSourceSummaryText();
        updateAllCatalogRowStates();
        renderLastConnectivityState();
    }

    private void updateSourceUiFromSelection() {
        boolean cloudSelected = isCloudSelected();
        binding.localModelCard.setVisibility(cloudSelected ? View.GONE : View.VISIBLE);
        binding.cloudConfigCard.setVisibility(cloudSelected ? View.VISIBLE : View.GONE);
        binding.importModelButton.setEnabled(!runningTest && !cloudSelected);
        binding.useModelButton.setEnabled(!runningTest && !cloudSelected);
        binding.cloudProviderInput.setEnabled(!runningTest && cloudSelected);
        binding.cloudApiKeyInput.setEnabled(!runningTest && cloudSelected);
        binding.cloudModelInput.setEnabled(!runningTest && cloudSelected);
        binding.cloudEndpointInput.setEnabled(!runningTest && cloudSelected);

        boolean customSelected = cloudSelected && LocalAiConfig.isCustomProvider(readSelectedCloudProviderId());
        binding.cloudEndpointLayout.setHelperText(customSelected
                ? "OpenAI-compatible endpoint. Ej.: http://192.168.1.10:11434/v1/chat/completions (Ollama), LM Studio, OpenRouter, Groq, vLLM."
                : null);
    }

    private void updateSourceSummaryText() {
        if (isCloudSelected()) {
            String providerId = readSelectedCloudProviderId();
            binding.sourceSummaryText.setText("Cloud mode: " + LocalAiConfig.getProviderDisplayName(providerId));
        } else {
            binding.sourceSummaryText.setText("Local mode: GGUF model + on-device llama runtime.");
        }
    }

    private String readSelectedCloudProviderId() {
        String selectedLabel = Helper.getText(binding.cloudProviderInput).trim();
        if (selectedLabel.isEmpty()) {
            return LocalAiConfig.PROVIDER_DEEPSEEK;
        }
        return AiProviderCatalog.getProviderIdFromLabel(selectedLabel);
    }

    private void applyProviderDefaults(String providerId, boolean forceOverwrite) {
        String defaultModel = LocalAiConfig.getDefaultModelForProvider(providerId);
        String defaultEndpoint = LocalAiConfig.getDefaultEndpointForProvider(providerId);

        if (forceOverwrite || Helper.getText(binding.cloudModelInput).trim().isEmpty()) {
            binding.cloudModelInput.setText(defaultModel);
        }
        if (forceOverwrite || Helper.getText(binding.cloudEndpointInput).trim().isEmpty()) {
            binding.cloudEndpointInput.setText(defaultEndpoint);
        }
    }

    private boolean saveConfig(boolean showToast) {
        try {
            LocalAiConfig config = readConfigFromFields();
            config.save(this);
            refreshUi();
            if (showToast) {
                SketchwareUtil.toast("AI settings saved");
            }
            return true;
        } catch (IllegalArgumentException e) {
            SketchwareUtil.toastError(e.getMessage(), Toast.LENGTH_LONG);
            return false;
        }
    }

    private LocalAiConfig readConfigFromFields() {
        LocalAiConfig config = LocalAiConfig.load(this);
        config.setContextSize((int) binding.contextSizeSlider.getValue());
        config.setThreads((int) binding.threadsSlider.getValue());
        config.setMaxTokens((int) binding.maxTokensSlider.getValue());
        config.setTemperature(binding.temperatureSlider.getValue());
        config.setTopP(binding.topPSlider.getValue());
        config.setTopK((int) binding.topKSlider.getValue());
        config.setPresencePenalty(binding.presencePenaltySlider.getValue());
        config.setThinkMode(binding.thinkModeSwitch.isChecked());

        if (isCloudSelected()) {
            String providerId = readSelectedCloudProviderId();
            config.setProviderId(providerId);
            config.setCloudApiKey(Helper.getText(binding.cloudApiKeyInput));
            config.setCloudModel(Helper.getText(binding.cloudModelInput));
            config.setCloudEndpoint(Helper.getText(binding.cloudEndpointInput));
            if (config.getCloudModel().isEmpty()) {
                config.setCloudModel(LocalAiConfig.getDefaultModelForProvider(providerId));
            }
            if (config.getCloudEndpoint().isEmpty()) {
                config.setCloudEndpoint(LocalAiConfig.getDefaultEndpointForProvider(providerId));
            }
        } else {
            config.setProviderId(LocalAiConfig.PROVIDER_LOCAL);
        }

        return config;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int readInt(String label, String value, int minValue) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < minValue) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be at least " + minValue + ".");
        }
    }

    private float readFloat(String label, String value, float minValue, float maxValue) {
        try {
            float parsed = Float.parseFloat(value.trim());
            if (parsed < minValue || parsed > maxValue) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be between " + minValue + " and " + maxValue + ".");
        }
    }

    private void runTestPrompt() {
        if (runningTest || !saveConfig(false)) {
            return;
        }

        String prompt = Helper.getText(binding.promptInput).trim();
        if (prompt.isEmpty()) {
            prompt = "Respond with one concise sentence confirming this AI source is ready.";
        }

        LocalAiConfig config = LocalAiConfig.load(this);
        LocalAiService.Callback callback = new LocalAiService.Callback() {
            @Override
            public void onStarted() {
                runningTest = true;
                String providerLabel = config.isCloudProvider()
                        ? LocalAiConfig.getProviderDisplayName(config.getProviderId())
                        : "Local AI";
                setBusy(true, "Starting " + providerLabel + "...");
                binding.resultText.setText("Starting " + providerLabel + "...");
            }

            @Override
            public void onStatus(String status) {
                setBusy(true, status);
                binding.resultText.setText(status);
            }

            @Override
            public void onSuccess(String response) {
                binding.resultText.setText(response.isEmpty() ? "The model returned an empty response." : response);
            }

            @Override
            public void onError(Throwable throwable) {
                binding.resultText.setText(throwable.getMessage());
            }

            @Override
            public void onFinished() {
                runningTest = false;
                setBusy(false, null);
                refreshUi();
            }
        };

        if (config.isCloudProvider()) {
            CloudAiService.getInstance().generate(this, prompt, callback);
        } else {
            LocalAiService.getInstance().generate(this, prompt, callback);
        }
    }

    private void runConnectivityCheck() {
        if (runningTest || !saveConfig(false)) {
            return;
        }

        LocalAiConfig config = LocalAiConfig.load(this);
        if (config.isCloudProvider()) {
            if (config.getCloudApiKey().isEmpty() && !LocalAiConfig.isCustomProvider(config.getProviderId())) {
                SketchwareUtil.toastError("Cloud API key is missing. Save it first.");
                String reason = "Cloud connectivity check failed: missing API key.";
                binding.resultText.setText(reason);
                markConnectivityCheck(CHECK_STATUS_ERROR, LocalAiConfig.getProviderDisplayName(config.getProviderId()), reason);
                return;
            }
            if (config.resolveCloudModel().isEmpty()) {
                SketchwareUtil.toastError("Cloud model is missing. Save it first.");
                String reason = "Cloud connectivity check failed: missing model.";
                binding.resultText.setText(reason);
                markConnectivityCheck(CHECK_STATUS_ERROR, LocalAiConfig.getProviderDisplayName(config.getProviderId()), reason);
                return;
            }
            if (config.resolveCloudEndpoint().isEmpty()) {
                SketchwareUtil.toastError("Cloud endpoint is missing. Save it first.");
                String reason = "Cloud connectivity check failed: missing endpoint.";
                binding.resultText.setText(reason);
                markConnectivityCheck(CHECK_STATUS_ERROR, LocalAiConfig.getProviderDisplayName(config.getProviderId()), reason);
                return;
            }
        } else if (!config.hasModel()) {
            SketchwareUtil.toastError("Import/select a local .gguf model first.");
            String reason = "Local readiness check failed: no model selected.";
            binding.resultText.setText(reason);
            markConnectivityCheck(CHECK_STATUS_ERROR, "Local AI", reason);
            return;
        }

        String prompt = "Reply with CONNECTION_OK only.";
        String providerLabel = config.isCloudProvider()
                ? LocalAiConfig.getProviderDisplayName(config.getProviderId())
                : "Local AI";

        LocalAiService.Callback callback = new LocalAiService.Callback() {
            @Override
            public void onStarted() {
                runningTest = true;
                setBusy(true, "Checking " + providerLabel + " connectivity...");
                binding.resultText.setText("Checking " + providerLabel + " connectivity...");
            }

            @Override
            public void onStatus(String status) {
                setBusy(true, status);
                binding.resultText.setText(status);
            }

            @Override
            public void onSuccess(String response) {
                String trimmed = response == null ? "" : response.trim();
                if (trimmed.isEmpty()) {
                    String reason = providerLabel + " responded with an empty message.";
                    binding.resultText.setText(reason);
                    markConnectivityCheck(CHECK_STATUS_ERROR, providerLabel, reason);
                    return;
                }

                String cleanMessage;
                if (trimmed.equalsIgnoreCase("CONNECTION_OK")
                        || trimmed.replaceAll("[^a-zA-Z]", "").equalsIgnoreCase("CONNECTION_OK")) {
                    cleanMessage = "Response received successfully.";
                } else {
                    cleanMessage = trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
                }
                binding.resultText.setText("Connectivity OK for " + providerLabel + ". " + cleanMessage);
                SketchwareUtil.toast("Connectivity OK: " + providerLabel);
                markConnectivityCheck(CHECK_STATUS_OK, providerLabel, cleanMessage);
            }

            @Override
            public void onError(Throwable throwable) {
                String message = providerLabel + " connectivity failed: " + throwable.getMessage();
                binding.resultText.setText(message);
                markConnectivityCheck(CHECK_STATUS_ERROR, providerLabel, throwable.getMessage());
            }

            @Override
            public void onFinished() {
                runningTest = false;
                setBusy(false, null);
                refreshUi();
            }
        };

        if (config.isCloudProvider()) {
            CloudAiService.getInstance().generate(this, prompt, callback);
        } else {
            LocalAiService.getInstance().generate(this, prompt, callback);
        }
    }

    private void markConnectivityCheck(String status, String providerLabel, String message) {
        long now = System.currentTimeMillis();
        getSharedPreferences(LocalAiConfig.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_CHECK_STATUS, status)
                .putString(PREF_LAST_CHECK_PROVIDER, providerLabel == null ? "" : providerLabel)
                .putString(PREF_LAST_CHECK_MESSAGE, message == null ? "" : message)
                .putLong(PREF_LAST_CHECK_TIME_MS, now)
                .apply();
        renderConnectivityBadge(status, providerLabel, now, message);
    }

    private void renderLastConnectivityState() {
        android.content.SharedPreferences prefs = getSharedPreferences(LocalAiConfig.PREFS_NAME, Context.MODE_PRIVATE);
        String status = prefs.getString(PREF_LAST_CHECK_STATUS, CHECK_STATUS_UNKNOWN);
        String provider = prefs.getString(PREF_LAST_CHECK_PROVIDER, "");
        String message = prefs.getString(PREF_LAST_CHECK_MESSAGE, "");
        long checkedAt = prefs.getLong(PREF_LAST_CHECK_TIME_MS, 0L);
        renderConnectivityBadge(status, provider, checkedAt, message);
    }

    private void renderConnectivityBadge(String status, String provider, long checkedAtMs, String message) {
        String normalizedStatus;
        if (CHECK_STATUS_OK.equals(status)) {
            normalizedStatus = CHECK_STATUS_OK;
        } else if (CHECK_STATUS_ERROR.equals(status)) {
            normalizedStatus = CHECK_STATUS_ERROR;
        } else {
            normalizedStatus = CHECK_STATUS_UNKNOWN;
        }

        int containerColor;
        int textColor;
        int iconColor;
        String badgeText;
        if (CHECK_STATUS_OK.equals(normalizedStatus)) {
            containerColor = 0xFF2E7D32;
            textColor = 0xFFFFFFFF;
            iconColor = 0xFF4CAF50;
            badgeText = "Connectivity OK";
        } else if (CHECK_STATUS_ERROR.equals(normalizedStatus)) {
            containerColor = 0xFFC62828;
            textColor = 0xFFFFFFFF;
            iconColor = 0xFFEF5350;
            badgeText = "Connectivity Failed";
        } else {
            containerColor = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorSurfaceVariant);
            textColor = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnSurfaceVariant);
            iconColor = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOutlineVariant);
            badgeText = "Not checked";
        }

        GradientDrawable badgeDrawable = new GradientDrawable();
        badgeDrawable.setShape(GradientDrawable.RECTANGLE);
        badgeDrawable.setCornerRadius(dp(999));
        badgeDrawable.setColor(containerColor);

        binding.connectionBadgeText.setBackground(badgeDrawable);
        binding.connectionBadgeText.setTextColor(textColor);
        binding.connectionBadgeText.setBackgroundTintList(ColorStateList.valueOf(containerColor));
        binding.connectionBadgeText.setText(badgeText);
        binding.connectionIcon.setImageTintList(ColorStateList.valueOf(iconColor));

        if (checkedAtMs <= 0L) {
            binding.connectionTimeText.setText("Last check: never");
            return;
        }

        String providerLabel = provider == null || provider.trim().isEmpty() ? "Unknown source" : provider.trim();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(checkedAtMs));
        String suffix = message == null || message.trim().isEmpty() ? "" : " | " + message.trim();
        binding.connectionTimeText.setText("Last check: " + timestamp + " | " + providerLabel + suffix);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void useSelectedModel() {
        if (runningTest || !saveConfig(false)) {
            return;
        }

        if (isCloudSelected()) {
            SketchwareUtil.toastError("Switch to Local source to load a GGUF model into RAM.");
            return;
        }

        LocalAiConfig config = LocalAiConfig.load(this);
        if (config.getModelPath().isEmpty()) {
            SketchwareUtil.toastError("Import a .gguf model first.");
            return;
        }

        LocalAiService.getInstance().loadModel(this, new LocalAiService.Callback() {
            @Override
            public void onStarted() {
                runningTest = true;
                setBusy(true, "Loading selected model into RAM...");
                binding.resultText.setText("Loading selected model into RAM...");
            }

            @Override
            public void onStatus(String status) {
                setBusy(true, status);
                binding.resultText.setText(status);
            }

            @Override
            public void onSuccess(String response) {
                binding.resultText.setText(response);
                SketchwareUtil.toast("Model loaded in RAM");
            }

            @Override
            public void onError(Throwable throwable) {
                binding.resultText.setText(throwable.getMessage());
            }

            @Override
            public void onFinished() {
                runningTest = false;
                setBusy(false, null);
                refreshUi();
            }
        });
    }

    private void setBusy(boolean busy, @Nullable String status) {
        binding.progressIndicator.setIndeterminate(true);
        binding.progressIndicator.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.statusText.setText(status == null ? "Ready" : status);
        binding.sourceLocalButton.setEnabled(!busy);
        binding.sourceCloudButton.setEnabled(!busy);
        binding.saveButton.setEnabled(!busy);
        binding.checkConnectionButton.setEnabled(!busy);
        binding.testButton.setEnabled(!busy);
        binding.cancelButton.setEnabled(busy);

        boolean cloudSelected = isCloudSelected();
        binding.importModelButton.setEnabled(!busy && !cloudSelected);
        binding.useModelButton.setEnabled(!busy && !cloudSelected);
        binding.cloudProviderInput.setEnabled(!busy && cloudSelected);
        binding.cloudApiKeyInput.setEnabled(!busy && cloudSelected);
        binding.cloudModelInput.setEnabled(!busy && cloudSelected);
        binding.cloudEndpointInput.setEnabled(!busy && cloudSelected);
        binding.contextSizeSlider.setEnabled(!busy);
        binding.threadsSlider.setEnabled(!busy);
        binding.maxTokensSlider.setEnabled(!busy);
        binding.temperatureSlider.setEnabled(!busy);
        binding.topPSlider.setEnabled(!busy);
        binding.topKSlider.setEnabled(!busy);
        binding.presencePenaltySlider.setEnabled(!busy);
        binding.thinkModeSwitch.setEnabled(!busy);
        binding.promptInput.setEnabled(!busy);
        binding.catalogCancelButton.setEnabled(!busy && activeDownloader != null);
        updateAllCatalogRowStates();
    }

    private void setImportProgress(int progress, String status) {
        binding.progressIndicator.setVisibility(View.VISIBLE);
        binding.progressIndicator.setIndeterminate(progress < 0);
        if (progress >= 0) {
            binding.progressIndicator.setMax(100);
            binding.progressIndicator.setProgressCompat(progress, true);
        }
        binding.statusText.setText(status);
        binding.sourceLocalButton.setEnabled(false);
        binding.sourceCloudButton.setEnabled(false);
        binding.importModelButton.setEnabled(false);
        binding.useModelButton.setEnabled(false);
        binding.saveButton.setEnabled(false);
        binding.checkConnectionButton.setEnabled(false);
        binding.testButton.setEnabled(false);
        binding.cancelButton.setEnabled(false);
        binding.contextSizeSlider.setEnabled(false);
        binding.threadsSlider.setEnabled(false);
        binding.maxTokensSlider.setEnabled(false);
        binding.temperatureSlider.setEnabled(false);
        binding.topPSlider.setEnabled(false);
        binding.topKSlider.setEnabled(false);
        binding.presencePenaltySlider.setEnabled(false);
        binding.thinkModeSwitch.setEnabled(false);
        binding.promptInput.setEnabled(false);
        binding.cloudProviderInput.setEnabled(false);
        binding.cloudApiKeyInput.setEnabled(false);
        binding.cloudModelInput.setEnabled(false);
        binding.cloudEndpointInput.setEnabled(false);
        binding.catalogCancelButton.setEnabled(false);
        for (CatalogRow row : catalogRows.values()) {
            row.downloadButton.setEnabled(false);
            row.importModelButton.setEnabled(false);
            row.enableSwitch.setEnabled(false);
            row.deleteButton.setEnabled(false);
            for (MaterialButton chip : row.quantChips.values()) {
                chip.setEnabled(false);
            }
        }
    }

    private boolean isCloudSelected() {
        return binding.sourceToggleGroup.getCheckedButtonId() == binding.sourceCloudButton.getId();
    }
}
