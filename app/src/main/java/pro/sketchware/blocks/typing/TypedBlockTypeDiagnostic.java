package pro.sketchware.blocks.typing;

public final class TypedBlockTypeDiagnostic {

    public final TypedBlockTypeDiagnosticCode code;
    public final TypedBlockTypeDiagnosticSeverity severity;
    public final String blockId;
    public final String opcode;
    public final int inputIndex;
    public final String inputName;
    public final TypedBlockValueType expectedType;
    public final TypedBlockValueType actualType;
    public final String referencedBlockId;
    public final String message;

    public TypedBlockTypeDiagnostic(TypedBlockTypeDiagnosticCode code,
                                    TypedBlockTypeDiagnosticSeverity severity,
                                    String blockId,
                                    String opcode,
                                    int inputIndex,
                                    String inputName,
                                    TypedBlockValueType expectedType,
                                    TypedBlockValueType actualType,
                                    String referencedBlockId,
                                    String message) {
        this.code = code == null ? TypedBlockTypeDiagnosticCode.INPUT_TYPE_MISMATCH : code;
        this.severity = severity == null ? TypedBlockTypeDiagnosticSeverity.WARNING : severity;
        this.blockId = blockId == null ? "" : blockId;
        this.opcode = opcode == null ? "" : opcode;
        this.inputIndex = Math.max(inputIndex, -1);
        this.inputName = inputName == null ? "" : inputName;
        this.expectedType = expectedType == null ? TypedBlockValueType.UNKNOWN : expectedType;
        this.actualType = actualType == null ? TypedBlockValueType.UNKNOWN : actualType;
        this.referencedBlockId = referencedBlockId == null ? "" : referencedBlockId;
        this.message = message == null ? "" : message;
    }
}
