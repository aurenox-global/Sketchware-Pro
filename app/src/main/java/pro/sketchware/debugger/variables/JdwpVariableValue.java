package pro.sketchware.debugger.variables;

public final class JdwpVariableValue {

    public static final JdwpVariableValue UNAVAILABLE = new JdwpVariableValue(
            JdwpVariableValueKind.UNAVAILABLE,
            "",
            "<unavailable>",
            "",
            false,
            0
    );

    public final JdwpVariableValueKind kind;
    public final String typeName;
    public final String displayValue;
    public final String objectId;
    public final boolean expandable;
    public final int childrenCount;

    public JdwpVariableValue(JdwpVariableValueKind kind,
                             String typeName,
                             String displayValue,
                             String objectId,
                             boolean expandable,
                             int childrenCount) {
        this.kind = kind == null ? JdwpVariableValueKind.UNAVAILABLE : kind;
        this.typeName = typeName == null ? "" : typeName;
        this.displayValue = displayValue == null ? "" : displayValue;
        this.objectId = objectId == null ? "" : objectId;
        this.expandable = expandable;
        this.childrenCount = Math.max(childrenCount, 0);
    }

    public static JdwpVariableValue primitive(String typeName, String displayValue) {
        return new JdwpVariableValue(
                JdwpVariableValueKind.PRIMITIVE,
                typeName,
                displayValue,
                "",
                false,
                0
        );
    }

    public static JdwpVariableValue object(String typeName,
                                           String displayValue,
                                           String objectId,
                                           boolean expandable,
                                           int childrenCount) {
        return new JdwpVariableValue(
                JdwpVariableValueKind.OBJECT,
                typeName,
                displayValue,
                objectId,
                expandable,
                childrenCount
        );
    }
}
