package pro.sketchware.accessibility;

public final class AccessibilityNodeSnapshot {

    public final String id;
    public final String label;
    public final String contentDescription;
    public final boolean clickable;
    public final boolean enabled;
    public final boolean importantForAccessibility;
    public final int widthDp;
    public final int heightDp;
    public final double textContrastRatio;

    public AccessibilityNodeSnapshot(String id,
                                     String label,
                                     String contentDescription,
                                     boolean clickable,
                                     boolean enabled,
                                     boolean importantForAccessibility,
                                     int widthDp,
                                     int heightDp,
                                     double textContrastRatio) {
        this.id = id == null ? "" : id.trim();
        this.label = label == null ? "" : label.trim();
        this.contentDescription = contentDescription == null ? "" : contentDescription.trim();
        this.clickable = clickable;
        this.enabled = enabled;
        this.importantForAccessibility = importantForAccessibility;
        this.widthDp = Math.max(widthDp, 0);
        this.heightDp = Math.max(heightDp, 0);
        this.textContrastRatio = Math.max(textContrastRatio, 0.0);
    }
}
