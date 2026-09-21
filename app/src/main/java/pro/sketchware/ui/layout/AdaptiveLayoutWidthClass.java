package pro.sketchware.ui.layout;

public enum AdaptiveLayoutWidthClass {

    COMPACT,
    MEDIUM,
    EXPANDED;

    public String label() {
        return switch (this) {
            case COMPACT -> "Compact";
            case MEDIUM -> "Medium";
            case EXPANDED -> "Expanded";
        };
    }
}
