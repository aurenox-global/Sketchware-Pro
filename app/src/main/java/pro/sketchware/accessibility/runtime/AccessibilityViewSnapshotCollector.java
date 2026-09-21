package pro.sketchware.accessibility.runtime;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.accessibility.AccessibilityNodeSnapshot;

public final class AccessibilityViewSnapshotCollector {

    private AccessibilityViewSnapshotCollector() {
    }

    public static List<AccessibilityNodeSnapshot> collect(View root) {
        ArrayList<AccessibilityNodeSnapshot> snapshots = new ArrayList<>();
        if (root == null) {
            return snapshots;
        }

        float density = root.getResources().getDisplayMetrics().density;
        traverse(root, snapshots, density);
        return snapshots;
    }

    private static void traverse(View view, List<AccessibilityNodeSnapshot> output, float density) {
        if (view == null) {
            return;
        }

        output.add(new AccessibilityNodeSnapshot(
                resolveNodeId(view),
                resolveLabel(view),
                resolveContentDescription(view),
                view.isClickable(),
                view.isEnabled(),
                isImportantForAccessibility(view),
                toDp(view.getWidth(), density),
                toDp(view.getHeight(), density),
                0.0
        ));

        if (view instanceof ViewGroup viewGroup) {
            for (int index = 0; index < viewGroup.getChildCount(); index++) {
                traverse(viewGroup.getChildAt(index), output, density);
            }
        }
    }

    private static String resolveNodeId(View view) {
        int id = view.getId();
        if (id == View.NO_ID) {
            return "view@" + Integer.toHexString(System.identityHashCode(view));
        }
        try {
            Resources resources = view.getResources();
            return resources.getResourceEntryName(id);
        } catch (Exception ignored) {
            return "id@" + id;
        }
    }

    private static String resolveLabel(View view) {
        if (view instanceof TextView textView) {
            CharSequence text = textView.getText();
            return text == null ? "" : text.toString().trim();
        }
        return "";
    }

    private static String resolveContentDescription(View view) {
        CharSequence contentDescription = view.getContentDescription();
        return contentDescription == null ? "" : contentDescription.toString().trim();
    }

    private static boolean isImportantForAccessibility(View view) {
        int mode = view.getImportantForAccessibility();
        return mode != View.IMPORTANT_FOR_ACCESSIBILITY_NO
                && mode != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
    }

    private static int toDp(int px, float density) {
        if (density <= 0f) {
            return Math.max(px, 0);
        }
        return Math.max(Math.round(px / density), 0);
    }
}
