package pro.sketchware.blocks.typing;

import java.util.Locale;

public enum TypedBlockValueType {

    VOID("void"),
    BOOLEAN("boolean"),
    NUMBER("number"),
    STRING("string"),
    LIST("list"),
    MAP("map"),
    COMPONENT("component"),
    VIEW("view"),
    INTENT("intent"),
    OBJECT("object"),
    ANY("any"),
    UNKNOWN("unknown");

    public final String id;

    TypedBlockValueType(String id) {
        this.id = id;
    }

    public static TypedBlockValueType fromLegacyBlockType(String token, String typeName) {
        String safeToken = token == null ? "" : token.trim();
        if (safeToken.isEmpty()) {
            return VOID;
        }

        if ("b".equals(safeToken)) {
            return BOOLEAN;
        }
        if ("d".equals(safeToken)) {
            return NUMBER;
        }
        if ("s".equals(safeToken)) {
            return STRING;
        }
        if ("v".equals(safeToken)) {
            TypedBlockValueType named = fromLegacyNamedType(typeName);
            return named == UNKNOWN ? OBJECT : named;
        }

        TypedBlockValueType named = fromLegacyNamedType(typeName);
        return named == UNKNOWN ? ANY : named;
    }

    public static TypedBlockValueType fromLegacyPlaceholder(String token, String typeName) {
        String safeToken = token == null ? "" : token.trim();
        if (safeToken.isEmpty()) {
            return UNKNOWN;
        }

        if ("b".equals(safeToken)) {
            return BOOLEAN;
        }
        if ("d".equals(safeToken)) {
            return NUMBER;
        }
        if ("s".equals(safeToken)) {
            return STRING;
        }
        if ("m".equals(safeToken)) {
            TypedBlockValueType named = fromLegacyNamedType(typeName);
            return named == UNKNOWN ? COMPONENT : named;
        }
        if ("v".equals(safeToken)) {
            TypedBlockValueType named = fromLegacyNamedType(typeName);
            return named == UNKNOWN ? OBJECT : named;
        }
        if ("l".equals(safeToken)) {
            return LIST;
        }

        return fromLegacyNamedType(typeName);
    }

    public boolean isAssignableFrom(TypedBlockValueType actual) {
        TypedBlockValueType safeActual = actual == null ? UNKNOWN : actual;
        if (this == ANY) {
            return true;
        }
        if (this == OBJECT) {
            return safeActual != VOID;
        }
        if (this == COMPONENT) {
            return safeActual == COMPONENT || safeActual == VIEW || safeActual == INTENT;
        }
        return this == safeActual;
    }

    private static TypedBlockValueType fromLegacyNamedType(String typeName) {
        String safeTypeName = typeName == null ? "" : typeName.trim().toLowerCase(Locale.ROOT);
        if (safeTypeName.isEmpty()) {
            return UNKNOWN;
        }
        if (safeTypeName.contains("list")) {
            return LIST;
        }
        if (safeTypeName.contains("map")) {
            return MAP;
        }
        if (safeTypeName.contains("view")) {
            return VIEW;
        }
        if (safeTypeName.contains("intent")) {
            return INTENT;
        }
        return OBJECT;
    }
}
