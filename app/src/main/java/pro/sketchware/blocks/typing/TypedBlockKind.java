package pro.sketchware.blocks.typing;

public enum TypedBlockKind {

    STATEMENT,
    VALUE,
    CONTROL,
    UNKNOWN;

    public static TypedBlockKind fromLegacyTypeToken(String token) {
        String safeToken = token == null ? "" : token.trim();
        if (safeToken.isEmpty()) {
            return STATEMENT;
        }
        if ("b".equals(safeToken)
                || "d".equals(safeToken)
                || "s".equals(safeToken)
                || "v".equals(safeToken)) {
            return VALUE;
        }
        if ("c".equals(safeToken) || "e".equals(safeToken) || "f".equals(safeToken)) {
            return CONTROL;
        }
        return UNKNOWN;
    }
}
