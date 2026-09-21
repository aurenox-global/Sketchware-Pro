package pro.sketchware.accessibility;

public final class AccessibilityIssue {

    public final String checkerId;
    public final String code;
    public final AccessibilityIssueSeverity severity;
    public final String nodeId;
    public final String message;

    public AccessibilityIssue(String checkerId,
                              String code,
                              AccessibilityIssueSeverity severity,
                              String nodeId,
                              String message) {
        this.checkerId = checkerId == null ? "" : checkerId;
        this.code = code == null ? "" : code;
        this.severity = severity == null ? AccessibilityIssueSeverity.WARNING : severity;
        this.nodeId = nodeId == null ? "" : nodeId;
        this.message = message == null ? "" : message;
    }
}
