package pro.sketchware.ui.layout;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

public final class AdaptiveLayoutPolicy {

    public static final int COMPACT_MAX_WIDTH_DP = 599;
    public static final int MEDIUM_MAX_WIDTH_DP = 839;

    private static final int COMPACT_PADDING_DP = 16;
    private static final int MEDIUM_PADDING_DP = 24;
    private static final int EXPANDED_PADDING_DP = 32;

    private static final int COMPACT_MAX_CONTENT_WIDTH_DP = 0;
    private static final int MEDIUM_MAX_CONTENT_WIDTH_DP = 840;
    private static final int EXPANDED_MAX_CONTENT_WIDTH_DP = 1120;

    private AdaptiveLayoutPolicy() {
    }

    public static AdaptiveLayoutSnapshot fromContext(Context context) {
        if (context == null) {
            return fromDimensionsDp(0, 0);
        }

        Configuration configuration = context.getResources().getConfiguration();
        int widthDp = configuration.screenWidthDp;
        int heightDp = configuration.screenHeightDp;

        if (widthDp <= 0 || heightDp <= 0) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            if (widthDp <= 0 && metrics.density > 0f) {
                widthDp = Math.round(metrics.widthPixels / metrics.density);
            }
            if (heightDp <= 0 && metrics.density > 0f) {
                heightDp = Math.round(metrics.heightPixels / metrics.density);
            }
        }

        return fromDimensionsDp(widthDp, heightDp);
    }

    public static AdaptiveLayoutSnapshot fromDimensionsDp(int widthDp, int heightDp) {
        int safeWidth = Math.max(widthDp, 0);
        int safeHeight = Math.max(heightDp, 0);

        AdaptiveLayoutWidthClass widthClass = toWidthClass(safeWidth);
        return switch (widthClass) {
            case COMPACT -> new AdaptiveLayoutSnapshot(
                    safeWidth,
                    safeHeight,
                    widthClass,
                    COMPACT_PADDING_DP,
                    COMPACT_MAX_CONTENT_WIDTH_DP,
                    false
            );
            case MEDIUM -> new AdaptiveLayoutSnapshot(
                    safeWidth,
                    safeHeight,
                    widthClass,
                    MEDIUM_PADDING_DP,
                    MEDIUM_MAX_CONTENT_WIDTH_DP,
                    false
            );
            case EXPANDED -> new AdaptiveLayoutSnapshot(
                    safeWidth,
                    safeHeight,
                    widthClass,
                    EXPANDED_PADDING_DP,
                    EXPANDED_MAX_CONTENT_WIDTH_DP,
                    true
            );
        };
    }

    public static AdaptiveLayoutWidthClass toWidthClass(int widthDp) {
        int safeWidth = Math.max(widthDp, 0);
        if (safeWidth <= COMPACT_MAX_WIDTH_DP) {
            return AdaptiveLayoutWidthClass.COMPACT;
        }
        if (safeWidth <= MEDIUM_MAX_WIDTH_DP) {
            return AdaptiveLayoutWidthClass.MEDIUM;
        }
        return AdaptiveLayoutWidthClass.EXPANDED;
    }

    public static int dpToPx(Context context, int dp) {
        if (context == null) {
            return dp;
        }
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
