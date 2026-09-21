package pro.sketchware.activities.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.PreferenceActivityBinding;
import pro.sketchware.featureflags.FeatureFlags;
import pro.sketchware.metrics.AccessibilityIssueReportStore;
import pro.sketchware.metrics.BuildMetricsStore;
import pro.sketchware.metrics.EditorPerformanceMetricsStore;
import pro.sketchware.metrics.KmpBuildPerformanceMetricsStore;
import pro.sketchware.metrics.KpiDashboardReleaseGatesResult;
import pro.sketchware.metrics.KpiDashboardStore;
import pro.sketchware.metrics.StartupPerformanceMetricsStore;
import pro.sketchware.ui.layout.AdaptiveLayoutPolicy;
import pro.sketchware.ui.layout.AdaptiveLayoutSnapshot;

public class FeatureFlagsActivity extends BaseAppCompatActivity {

    private PreferenceActivityBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = PreferenceActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.topAppBar.setTitle("Feature Flags");
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        onAdaptiveLayoutChanged(getAdaptiveLayoutSnapshot());

        getSupportFragmentManager().beginTransaction()
                .replace(binding.fragmentContainer.getId(), new FeatureFlagsFragment())
                .commit();
    }

    @Override
    protected void onAdaptiveLayoutChanged(@androidx.annotation.NonNull AdaptiveLayoutSnapshot snapshot) {
        if (binding == null) {
            return;
        }

        int horizontalPaddingPx = AdaptiveLayoutPolicy.dpToPx(this, snapshot.contentHorizontalPaddingDp);
        binding.contentLayout.setPaddingRelative(
                horizontalPaddingPx,
                binding.contentLayout.getPaddingTop(),
                horizontalPaddingPx,
                binding.contentLayout.getPaddingBottom()
        );
        binding.topAppBar.setSubtitle("Layout: " + snapshot.widthClass.label() + " (" + snapshot.widthDp + "dp)");
    }

    public static class FeatureFlagsFragment extends PreferenceFragmentCompat {

        private static final String KEY_BUILD_METRICS_SUMMARY = "build_metrics_summary";
        private static final String KEY_BUILD_METRICS_LAST = "build_metrics_last";
        private static final String KEY_BUILD_METRICS_STAGES = "build_metrics_stages";
        private static final String KEY_BUILD_METRICS_RESET = "build_metrics_reset";
        private static final String KEY_KMP_BUILD_METRICS_SUMMARY = "kmp_build_metrics_summary";
        private static final String KEY_KMP_BUILD_METRICS_TREND = "kmp_build_metrics_trend";
        private static final String KEY_KMP_BUILD_METRICS_STAGES = "kmp_build_metrics_stages";
        private static final String KEY_KMP_BUILD_METRICS_RESET = "kmp_build_metrics_reset";
        private static final String KEY_KMP_DEVELOPER_DOCS_ENTRY = "kmp_developer_docs_entry";
        private static final String KEY_EDITOR_METRICS_SUMMARY = "editor_metrics_summary";
        private static final String KEY_EDITOR_METRICS_LSP = "editor_metrics_lsp";
        private static final String KEY_EDITOR_METRICS_DIAGNOSTICS = "editor_metrics_diagnostics";
        private static final String KEY_EDITOR_METRICS_RESET = "editor_metrics_reset";
        private static final String KEY_STARTUP_METRICS_SUMMARY = "startup_metrics_summary";
        private static final String KEY_STARTUP_METRICS_LAST = "startup_metrics_last";
        private static final String KEY_STARTUP_METRICS_THRESHOLD = "startup_metrics_threshold";
        private static final String KEY_STARTUP_METRICS_RESET = "startup_metrics_reset";
        private static final String KEY_ACCESSIBILITY_METRICS_SUMMARY = "accessibility_metrics_summary";
        private static final String KEY_ACCESSIBILITY_METRICS_LAST = "accessibility_metrics_last";
        private static final String KEY_ACCESSIBILITY_METRICS_RESET = "accessibility_metrics_reset";
        private static final String KEY_KPI_DASHBOARD_SUMMARY = "kpi_dashboard_summary";
        private static final String KEY_KPI_RELEASE_GATES = "kpi_release_gates";
        private static final String KEY_KPI_DASHBOARD_RESET = "kpi_dashboard_reset";

        private Preference buildSummaryPreference;
        private Preference buildLastPreference;
        private Preference buildStagesPreference;
        private Preference kmpBuildSummaryPreference;
        private Preference kmpBuildTrendPreference;
        private Preference kmpBuildStagesPreference;
        private Preference editorSummaryPreference;
        private Preference editorLspPreference;
        private Preference editorDiagnosticsPreference;
        private Preference startupSummaryPreference;
        private Preference startupLastPreference;
        private Preference startupThresholdPreference;
        private Preference accessibilitySummaryPreference;
        private Preference accessibilityLastPreference;
        private Preference kpiSummaryPreference;
        private Preference kpiReleaseGatesPreference;

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            getPreferenceManager().setPreferenceDataStore(new FeatureFlagsPreferenceDataStore(requireContext().getApplicationContext()));
            setPreferencesFromResource(R.xml.preferences_feature_flags, rootKey);

            buildSummaryPreference = findPreference(KEY_BUILD_METRICS_SUMMARY);
            buildLastPreference = findPreference(KEY_BUILD_METRICS_LAST);
            buildStagesPreference = findPreference(KEY_BUILD_METRICS_STAGES);
            kmpBuildSummaryPreference = findPreference(KEY_KMP_BUILD_METRICS_SUMMARY);
            kmpBuildTrendPreference = findPreference(KEY_KMP_BUILD_METRICS_TREND);
            kmpBuildStagesPreference = findPreference(KEY_KMP_BUILD_METRICS_STAGES);
            editorSummaryPreference = findPreference(KEY_EDITOR_METRICS_SUMMARY);
            editorLspPreference = findPreference(KEY_EDITOR_METRICS_LSP);
            editorDiagnosticsPreference = findPreference(KEY_EDITOR_METRICS_DIAGNOSTICS);
            startupSummaryPreference = findPreference(KEY_STARTUP_METRICS_SUMMARY);
            startupLastPreference = findPreference(KEY_STARTUP_METRICS_LAST);
            startupThresholdPreference = findPreference(KEY_STARTUP_METRICS_THRESHOLD);
            accessibilitySummaryPreference = findPreference(KEY_ACCESSIBILITY_METRICS_SUMMARY);
            accessibilityLastPreference = findPreference(KEY_ACCESSIBILITY_METRICS_LAST);
            kpiSummaryPreference = findPreference(KEY_KPI_DASHBOARD_SUMMARY);
            kpiReleaseGatesPreference = findPreference(KEY_KPI_RELEASE_GATES);

            Preference resetPreference = findPreference(KEY_BUILD_METRICS_RESET);
            if (resetPreference != null) {
                resetPreference.setOnPreferenceClickListener(preference -> {
                    BuildMetricsStore.clear(requireContext().getApplicationContext());
                    refreshBuildMetrics();
                    return true;
                });
            }

            Preference resetEditorPreference = findPreference(KEY_EDITOR_METRICS_RESET);
            if (resetEditorPreference != null) {
                resetEditorPreference.setOnPreferenceClickListener(preference -> {
                    EditorPerformanceMetricsStore.clear(requireContext().getApplicationContext());
                    refreshEditorMetrics();
                    return true;
                });
            }

            Preference resetKmpBuildPreference = findPreference(KEY_KMP_BUILD_METRICS_RESET);
            if (resetKmpBuildPreference != null) {
                resetKmpBuildPreference.setOnPreferenceClickListener(preference -> {
                    KmpBuildPerformanceMetricsStore.clear(requireContext().getApplicationContext());
                    refreshKmpBuildMetrics();
                    refreshKpiDashboardMetrics();
                    return true;
                });
            }

            Preference kmpDeveloperDocsPreference = findPreference(KEY_KMP_DEVELOPER_DOCS_ENTRY);
            if (kmpDeveloperDocsPreference != null) {
                kmpDeveloperDocsPreference.setOnPreferenceClickListener(preference -> {
                    showKmpDeveloperDocsDialog();
                    return true;
                });
            }

            Preference resetStartupPreference = findPreference(KEY_STARTUP_METRICS_RESET);
            if (resetStartupPreference != null) {
                resetStartupPreference.setOnPreferenceClickListener(preference -> {
                    StartupPerformanceMetricsStore.clear(requireContext().getApplicationContext());
                    refreshStartupMetrics();
                    return true;
                });
            }

            Preference resetAccessibilityPreference = findPreference(KEY_ACCESSIBILITY_METRICS_RESET);
            if (resetAccessibilityPreference != null) {
                resetAccessibilityPreference.setOnPreferenceClickListener(preference -> {
                    AccessibilityIssueReportStore.clear(requireContext().getApplicationContext());
                    refreshAccessibilityMetrics();
                    refreshKpiDashboardMetrics();
                    return true;
                });
            }

            Preference resetKpiPreference = findPreference(KEY_KPI_DASHBOARD_RESET);
            if (resetKpiPreference != null) {
                resetKpiPreference.setOnPreferenceClickListener(preference -> {
                    KpiDashboardStore.clearAllMetrics(requireContext().getApplicationContext());
                    refreshBuildMetrics();
                    refreshKmpBuildMetrics();
                    refreshEditorMetrics();
                    refreshStartupMetrics();
                    refreshAccessibilityMetrics();
                    refreshKpiDashboardMetrics();
                    return true;
                });
            }

            refreshBuildMetrics();
            refreshKmpBuildMetrics();
            refreshEditorMetrics();
            refreshStartupMetrics();
            refreshAccessibilityMetrics();
            refreshKpiDashboardMetrics();
        }

        @Override
        public void onResume() {
            super.onResume();
            refreshBuildMetrics();
            refreshKmpBuildMetrics();
            refreshEditorMetrics();
            refreshStartupMetrics();
            refreshAccessibilityMetrics();
            refreshKpiDashboardMetrics();
        }

        private void refreshBuildMetrics() {
            BuildMetricsStore.Snapshot snapshot = BuildMetricsStore.snapshot(requireContext().getApplicationContext());

            if (snapshot.totalCount == 0) {
                if (buildSummaryPreference != null) {
                    buildSummaryPreference.setSummary("No data yet. Run a build to collect metrics.");
                }
                if (buildLastPreference != null) {
                    buildLastPreference.setSummary("No build has been recorded.");
                }
                if (buildStagesPreference != null) {
                    buildStagesPreference.setSummary("No stage data yet.");
                }
                return;
            }

            if (buildSummaryPreference != null) {
                buildSummaryPreference.setSummary(
                        "Total: " + snapshot.totalCount
                                + " | Success: " + snapshot.successCount
                                + " | Failed: " + snapshot.failureCount
                                + " | Canceled: " + snapshot.canceledCount
                                + " | Cold: " + snapshot.coldCount
                                + " | Incremental: " + snapshot.incrementalCount
                        + " | Cache hit est: " + BuildMetricsStore.formatPercent(snapshot.cacheHitEstimateRate)
                        + " (" + snapshot.cacheHitEstimateCount + "/"
                        + (snapshot.cacheHitEstimateCount + snapshot.cacheMissEstimateCount) + ")"
                                + " | Avg success: " + BuildMetricsStore.formatDuration(snapshot.averageSuccessDurationMs)
                );
            }

            if (buildLastPreference != null) {
                String status = snapshot.lastCanceled ? "Canceled" : (snapshot.lastSuccess ? "Success" : "Failed");
                buildLastPreference.setSummary(
                        "Type: " + snapshot.lastType
                                + " | Profile: " + snapshot.lastProfile
                                + " | Status: " + status
                                + " | Duration: " + BuildMetricsStore.formatDuration(snapshot.lastDurationMs)
                );
            }

            if (buildStagesPreference != null) {
                buildStagesPreference.setSummary(
                        BuildMetricsStore.formatStageSummary(snapshot.lastStageDurations, 4)
                );
            }
        }

        private void refreshEditorMetrics() {
            EditorPerformanceMetricsStore.Snapshot snapshot =
                    EditorPerformanceMetricsStore.snapshot(requireContext().getApplicationContext());

            if (snapshot.totalSamples == 0) {
                if (editorSummaryPreference != null) {
                    editorSummaryPreference.setSummary("No data yet. Use the editor with LSP enabled to collect metrics.");
                }
                if (editorLspPreference != null) {
                    editorLspPreference.setSummary("No LSP latency samples yet.");
                }
                if (editorDiagnosticsPreference != null) {
                    editorDiagnosticsPreference.setSummary("No diagnostics latency samples yet.");
                }
                return;
            }

            if (editorSummaryPreference != null) {
                int lspSamples = snapshot.completion.count + snapshot.definition.count + snapshot.references.count;
                int diagnosticsSamples = snapshot.diagnosticsAnalyze.count
                        + snapshot.diagnosticsRender.count
                        + snapshot.diagnosticsPublishToRender.count;
                editorSummaryPreference.setSummary(
                        "Total: " + snapshot.totalSamples
                                + " | LSP ops: " + lspSamples
                                + " | Diagnostics ops: " + diagnosticsSamples
                );
            }

            if (editorLspPreference != null) {
                String lspSummary = EditorPerformanceMetricsStore.formatOperation("completion", snapshot.completion)
                        + "\n"
                        + EditorPerformanceMetricsStore.formatOperation("definition", snapshot.definition)
                        + "\n"
                        + EditorPerformanceMetricsStore.formatOperation("references", snapshot.references);
                editorLspPreference.setSummary(lspSummary);
            }

            if (editorDiagnosticsPreference != null) {
                String diagnosticsSummary =
                        EditorPerformanceMetricsStore.formatOperation("analyze", snapshot.diagnosticsAnalyze)
                                + "\n"
                                + EditorPerformanceMetricsStore.formatOperation("render", snapshot.diagnosticsRender)
                                + "\n"
                                + EditorPerformanceMetricsStore.formatOperation("publish->render", snapshot.diagnosticsPublishToRender);
                editorDiagnosticsPreference.setSummary(diagnosticsSummary);
            }
        }

        private void refreshKmpBuildMetrics() {
            KmpBuildPerformanceMetricsStore.Snapshot snapshot =
                    KmpBuildPerformanceMetricsStore.snapshot(requireContext().getApplicationContext());

            if (snapshot.totalRuns == 0) {
                if (kmpBuildSummaryPreference != null) {
                    kmpBuildSummaryPreference.setSummary("No data yet. Run KMP experimental builds to collect metrics.");
                }
                if (kmpBuildTrendPreference != null) {
                    kmpBuildTrendPreference.setSummary("No trend data yet.");
                }
                if (kmpBuildStagesPreference != null) {
                    kmpBuildStagesPreference.setSummary("No stage data yet.");
                }
                return;
            }

            if (kmpBuildSummaryPreference != null) {
                kmpBuildSummaryPreference.setSummary(
                        "Runs: " + snapshot.totalRuns
                                + " | Cold: " + snapshot.coldRuns
                                + " | Incremental: " + snapshot.incrementalRuns
                                + " | Last profile: " + snapshot.lastProfile
                                + " | Last duration: " + KmpBuildPerformanceMetricsStore.formatDuration(snapshot.lastDurationMs)
                );
            }

            if (kmpBuildTrendPreference != null) {
                String trendSummary = KmpBuildPerformanceMetricsStore.formatOperation("total", snapshot.totalDurationTrend)
                        + "\n"
                        + KmpBuildPerformanceMetricsStore.formatTargetTrend(snapshot.targetDurationTrends, 3);
                kmpBuildTrendPreference.setSummary(trendSummary);
            }

            if (kmpBuildStagesPreference != null) {
                kmpBuildStagesPreference.setSummary(
                        KmpBuildPerformanceMetricsStore.formatStageSummary(snapshot.lastTargetStages, 4)
                );
            }
        }

        private void refreshStartupMetrics() {
            StartupPerformanceMetricsStore.Snapshot snapshot =
                    StartupPerformanceMetricsStore.snapshot(requireContext().getApplicationContext());

            if (snapshot.totalCount == 0) {
                if (startupSummaryPreference != null) {
                    startupSummaryPreference.setSummary("No data yet. Open the app with startup profiling enabled to collect samples.");
                }
                if (startupLastPreference != null) {
                    startupLastPreference.setSummary("No startup has been recorded.");
                }
                if (startupThresholdPreference != null) {
                    startupThresholdPreference.setSummary(
                            "Threshold: " + StartupPerformanceMetricsStore.formatDuration(snapshot.activeThresholdMs)
                    );
                }
                return;
            }

            if (startupSummaryPreference != null) {
                startupSummaryPreference.setSummary(
                        "Total: " + snapshot.totalCount
                                + " | Regressions: " + snapshot.regressionCount
                                + " (" + StartupPerformanceMetricsStore.formatPercent(snapshot.regressionRate) + ")"
                                + "\n"
                                + StartupPerformanceMetricsStore.formatOperation("startup.total", snapshot.total)
                );
            }

            if (startupLastPreference != null) {
                startupLastPreference.setSummary(
                        "Total: " + StartupPerformanceMetricsStore.formatDuration(snapshot.lastTotalDurationMs)
                                + " | app.init: " + StartupPerformanceMetricsStore.formatDuration(snapshot.lastApplicationInitDurationMs)
                                + " | main.ready: " + StartupPerformanceMetricsStore.formatDuration(snapshot.lastMainActivityDurationMs)
                );
            }

            if (startupThresholdPreference != null) {
                String status = snapshot.lastRegression ? "REGRESSION" : "ok";
                startupThresholdPreference.setSummary(
                        "Last status: " + status
                                + " | Last threshold: " + StartupPerformanceMetricsStore.formatDuration(snapshot.lastThresholdMs)
                                + " | Active threshold: " + StartupPerformanceMetricsStore.formatDuration(snapshot.activeThresholdMs)
                                + " | Baseline p50: " + StartupPerformanceMetricsStore.formatDuration(snapshot.lastBaselineP50Ms)
                );
            }
        }

        private void refreshAccessibilityMetrics() {
            AccessibilityIssueReportStore.Snapshot snapshot =
                    AccessibilityIssueReportStore.snapshot(requireContext().getApplicationContext());

            if (snapshot.totalScans == 0) {
                if (accessibilitySummaryPreference != null) {
                    accessibilitySummaryPreference.setSummary("No data yet. Open screens with accessibility reporting enabled to collect checks.");
                }
                if (accessibilityLastPreference != null) {
                    accessibilityLastPreference.setSummary("No accessibility report has been recorded.");
                }
                return;
            }

            if (accessibilitySummaryPreference != null) {
                accessibilitySummaryPreference.setSummary(
                        "Scans: " + snapshot.totalScans
                                + " | Issues: " + snapshot.totalIssues
                                + " (" + AccessibilityIssueReportStore.formatPercent(snapshot.issueRate) + ")"
                                + " | Warnings: " + snapshot.totalWarnings
                                + " | Errors: " + snapshot.totalErrors
                );
            }

            if (accessibilityLastPreference != null) {
                accessibilityLastPreference.setSummary(
                        "Screen: " + snapshot.lastScreenId
                                + " | Issues: " + snapshot.lastIssueCount
                                + " (W: " + snapshot.lastWarningCount + ", E: " + snapshot.lastErrorCount + ")"
                                + " | Duration: " + AccessibilityIssueReportStore.formatDuration(snapshot.lastDurationMs)
                );
            }
        }

        private void refreshKpiDashboardMetrics() {
            if (!FeatureFlags.isEnabled(requireContext().getApplicationContext(), FeatureFlags.Key.KPI_DASHBOARD_RELEASE_GATES)) {
                if (kpiSummaryPreference != null) {
                    kpiSummaryPreference.setSummary("Disabled. Enable KPI Dashboard and Release Gates to evaluate rollout readiness.");
                }
                if (kpiReleaseGatesPreference != null) {
                    kpiReleaseGatesPreference.setSummary("Disabled.");
                }
                return;
            }

            var snapshot = KpiDashboardStore.snapshot(requireContext().getApplicationContext());
            KpiDashboardReleaseGatesResult gates = KpiDashboardStore.evaluateReleaseGates(requireContext().getApplicationContext());

            if (kpiSummaryPreference != null) {
                kpiSummaryPreference.setSummary(
                        "Builds: " + snapshot.build.totalCount
                                + " | KMP runs: " + snapshot.kmpBuild.totalRuns
                                + " | Editor samples: " + snapshot.editor.totalSamples
                                + " | Startup samples: " + snapshot.startup.totalCount
                                + " | Accessibility scans: " + snapshot.accessibility.totalScans
                );
            }

            if (kpiReleaseGatesPreference != null) {
                kpiReleaseGatesPreference.setSummary(
                        "Overall: " + gates.overallStatus().name()
                                + " | PASS " + gates.passCount
                                + " | WARN " + gates.warnCount
                                + " | FAIL " + gates.failCount
                );
            }
        }

        private void showKmpDeveloperDocsDialog() {
            StringBuilder message = new StringBuilder();
            message.append("Guide path: docs/kmp_developer_docs.md\n\n")
                    .append("Sample path: docs/kmp_sample_app\n\n")
                    .append("Build command from repository root:\n")
                    .append("./gradlew -p docs/kmp_sample_app --no-daemon :androidApp:assembleDebug :desktopApp:desktopJar\n\n")
                    .append("Expected artifacts:\n")
                    .append("- Android APK in androidApp/build/outputs/apk/debug\n")
                    .append("- Desktop JAR in desktopApp/build/libs");

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("KMP Developer Docs")
                    .setMessage(message.toString())
                    .setPositiveButton("Open Docs Site", (dialog, which) -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.sketchware.pro"));
                        startActivity(intent);
                    })
                    .setNegativeButton("Close", null)
                    .show();
        }
    }

    private static class FeatureFlagsPreferenceDataStore extends PreferenceDataStore {
        private final android.content.Context context;

        FeatureFlagsPreferenceDataStore(android.content.Context context) {
            this.context = context;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            FeatureFlags.setEnabled(context, FeatureFlags.Key.valueOf(key), value);
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            try {
                return FeatureFlags.isEnabled(context, FeatureFlags.Key.valueOf(key));
            } catch (IllegalArgumentException ignored) {
                return defValue;
            }
        }
    }
}