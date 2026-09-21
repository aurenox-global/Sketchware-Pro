package pro.sketchware.accessibility.runtime;

import android.content.Context;
import android.view.View;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import pro.sketchware.accessibility.AccessibilityCheckRequest;
import pro.sketchware.accessibility.AccessibilityChecksEngine;
import pro.sketchware.accessibility.AccessibilityIssueReport;
import pro.sketchware.featureflags.FeatureFlags;
import pro.sketchware.metrics.AccessibilityIssueReportStore;

public final class AccessibilityRuntimeReporter {

    private static final AccessibilityChecksEngine ENGINE = AccessibilityChecksEngine.createDefault();
    private static final Set<String> REPORTED_SCREEN_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private AccessibilityRuntimeReporter() {
    }

    public static void reportViewHierarchy(Context context, String screenId, View rootView) {
        if (context == null || rootView == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (!FeatureFlags.isEnabled(appContext, FeatureFlags.Key.ACCESSIBILITY_CHECKS_REPORTING)) {
            return;
        }

        String safeScreenId = screenId == null ? "" : screenId.trim();
        if (safeScreenId.isEmpty()) {
            safeScreenId = rootView.getClass().getSimpleName();
        }

        if (!REPORTED_SCREEN_IDS.add(safeScreenId)) {
            return;
        }

        AccessibilityIssueReport report = ENGINE.run(
                new AccessibilityCheckRequest(
                        safeScreenId,
                        AccessibilityViewSnapshotCollector.collect(rootView)
                )
        );
        AccessibilityIssueReportStore.recordReport(appContext, report);
    }
}
