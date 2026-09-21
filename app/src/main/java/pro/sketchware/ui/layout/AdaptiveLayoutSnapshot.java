package pro.sketchware.ui.layout;

public final class AdaptiveLayoutSnapshot {

    public final int widthDp;
    public final int heightDp;
    public final AdaptiveLayoutWidthClass widthClass;
    public final int contentHorizontalPaddingDp;
    public final int recommendedMaxContentWidthDp;
    public final boolean preferTwoPane;

    public AdaptiveLayoutSnapshot(int widthDp,
                                  int heightDp,
                                  AdaptiveLayoutWidthClass widthClass,
                                  int contentHorizontalPaddingDp,
                                  int recommendedMaxContentWidthDp,
                                  boolean preferTwoPane) {
        this.widthDp = Math.max(widthDp, 0);
        this.heightDp = Math.max(heightDp, 0);
        this.widthClass = widthClass == null ? AdaptiveLayoutWidthClass.COMPACT : widthClass;
        this.contentHorizontalPaddingDp = Math.max(contentHorizontalPaddingDp, 0);
        this.recommendedMaxContentWidthDp = Math.max(recommendedMaxContentWidthDp, 0);
        this.preferTwoPane = preferTwoPane;
    }
}
