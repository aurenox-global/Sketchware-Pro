package pro.sketchware.blocks.typing;

public final class TypedBlockInputPort {

    public final int index;
    public final String name;
    public final TypedBlockValueType expectedType;
    public final String rawToken;
    public final String sourceTypeName;

    public TypedBlockInputPort(int index,
                               String name,
                               TypedBlockValueType expectedType,
                               String rawToken,
                               String sourceTypeName) {
        this.index = Math.max(index, 0);
        this.name = name == null ? "" : name;
        this.expectedType = expectedType == null ? TypedBlockValueType.UNKNOWN : expectedType;
        this.rawToken = rawToken == null ? "" : rawToken;
        this.sourceTypeName = sourceTypeName == null ? "" : sourceTypeName;
    }
}
