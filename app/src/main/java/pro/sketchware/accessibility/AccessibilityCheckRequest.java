package pro.sketchware.accessibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AccessibilityCheckRequest {

    public final String screenId;
    public final List<AccessibilityNodeSnapshot> nodes;

    public AccessibilityCheckRequest(String screenId, List<AccessibilityNodeSnapshot> nodes) {
        this.screenId = screenId == null ? "" : screenId.trim();
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes == null
                ? Collections.emptyList()
                : nodes));
    }
}
