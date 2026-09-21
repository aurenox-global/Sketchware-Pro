package pro.sketchware.blocks.typing;

public enum TypedBlockTypeDiagnosticSeverity {

    WARNING,
    ERROR;

    public boolean isError() {
        return this == ERROR;
    }
}
