package pro.sketchware.blocks.typing;

import java.util.Collection;
import java.util.Locale;

public enum TypedBlockScope {
    COMMON,
    ANDROID_ONLY,
    DESKTOP_ONLY,
    IOS_ONLY,
    JVM,
    NATIVE,
    ALL_EXCEPT_IOS;

    public boolean supportsTargetId(String targetId) {
        String normalized = normalizeTarget(targetId);
        if (normalized.isEmpty()) {
            return this == COMMON;
        }

        switch (this) {
            case COMMON:
                return true;
            case ANDROID_ONLY:
                return "ANDROID".equals(normalized);
            case DESKTOP_ONLY:
                return "DESKTOP".equals(normalized);
            case IOS_ONLY:
                return normalized.startsWith("IOS");
            case JVM:
                return "ANDROID".equals(normalized) || "DESKTOP".equals(normalized);
            case NATIVE:
                return normalized.startsWith("IOS") || normalized.startsWith("LINUX") || normalized.startsWith("MACOS");
            case ALL_EXCEPT_IOS:
                return !normalized.startsWith("IOS");
            default:
                return false;
        }
    }

    public boolean supportsAny(Collection<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return this == COMMON;
        }

        for (String targetId : targetIds) {
            if (supportsTargetId(targetId)) {
                return true;
            }
        }
        return false;
    }

    public static TypedBlockScope inferFromLegacy(String opcode, String typeName, String spec) {
        String signals = (safe(opcode) + " " + safe(typeName) + " " + safe(spec)).toLowerCase(Locale.ROOT);

        if (containsAny(signals,
                "javax.swing",
                "java.awt",
                "jframe",
                " desktop")) {
            return DESKTOP_ONLY;
        }

        if (containsAny(signals,
                "android.",
                " activity",
                "%m.activity",
                "%m.view",
                "%m.listview",
                "%m.edittext",
                "%m.intent",
                "fragment",
                "recyclerview",
                "progressdialog",
                "datepicker",
                "timepicker",
                "sharedpreferences",
                "toast")) {
            return ANDROID_ONLY;
        }

        if (containsAny(signals, " ios", "ios_")) {
            return IOS_ONLY;
        }

        if (containsAny(signals, " native", "kotlin/native")) {
            return NATIVE;
        }

        if (containsAny(signals, " jvm", "kotlin.jvm")) {
            return JVM;
        }

        return COMMON;
    }

    private static String normalizeTarget(String targetId) {
        if (targetId == null) {
            return "";
        }

        return targetId
                .trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
