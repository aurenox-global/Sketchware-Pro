package pro.sketchware.blocks.typing;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class TypedBlockTargetCompatibilityValidator {

    private final TypedBlockRegistry registry;

    public TypedBlockTargetCompatibilityValidator() {
        this(new TypedBlockRegistry());
    }

    public TypedBlockTargetCompatibilityValidator(TypedBlockRegistry registry) {
        this.registry = registry == null ? new TypedBlockRegistry() : registry;
    }

    public List<TypedBlockTypeDiagnostic> validate(List<BlockBean> blocks, Collection<String> enabledTargetIds) {
        if (blocks == null || blocks.isEmpty()) {
            return Collections.emptyList();
        }

        Collection<String> normalizedTargets = normalizeTargets(enabledTargetIds);
        ArrayList<TypedBlockTypeDiagnostic> diagnostics = new ArrayList<>();

        for (BlockBean block : blocks) {
            if (block == null) {
                continue;
            }

            TypedBlockSignature signature = resolveSignature(block);
            if (signature == null) {
                continue;
            }

            if (signature.scope.supportsAny(normalizedTargets)) {
                continue;
            }

            diagnostics.add(new TypedBlockTypeDiagnostic(
                    TypedBlockTypeDiagnosticCode.TARGET_SCOPE_INCOMPATIBLE,
                    TypedBlockTypeDiagnosticSeverity.WARNING,
                    normalize(block.id),
                    signature.opcode,
                    -1,
                    "",
                    TypedBlockValueType.UNKNOWN,
                    TypedBlockValueType.UNKNOWN,
                    "",
                        "Block scope " + signature.scope.name()
                            + " is incompatible with enabled targets " + formatTargets(normalizedTargets)
                            + ". " + buildSuggestion(signature.scope)
            ));
        }

        return diagnostics;
    }

    private TypedBlockSignature resolveSignature(BlockBean block) {
        String opcode = normalize(block == null ? null : block.opCode);
        TypedBlockSignature fromRegistry = registry.find(opcode);
        if (fromRegistry != null) {
            return fromRegistry;
        }
        return block == null
                ? TypedBlockSignature.fromLegacy("", "", "", "")
                : block.toTypedSignature();
    }

    private static Collection<String> normalizeTargets(Collection<String> enabledTargetIds) {
        if (enabledTargetIds == null || enabledTargetIds.isEmpty()) {
            return Collections.singletonList("COMMON");
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String targetId : enabledTargetIds) {
            String safe = normalize(targetId)
                    .replace('-', '_')
                    .replace(' ', '_')
                    .toUpperCase();
            if (!safe.isEmpty()) {
                normalized.add(safe);
            }
        }

        if (normalized.isEmpty()) {
            normalized.add("COMMON");
        }
        return normalized;
    }

    private static String formatTargets(Collection<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return "[COMMON]";
        }

        StringBuilder result = new StringBuilder("[");
        int index = 0;
        for (String targetId : targetIds) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(targetId);
            index++;
        }
        result.append(']');
        return result.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String buildSuggestion(TypedBlockScope scope) {
        if (scope == null) {
            return "Consider replacing this block with a COMMON-compatible alternative.";
        }

        switch (scope) {
            case ANDROID_ONLY:
                return "Use an Android target or replace with a COMMON-compatible block.";
            case DESKTOP_ONLY:
                return "Use a Desktop target or replace with a COMMON-compatible block.";
            case IOS_ONLY:
                return "Use an iOS target or move this logic to an iOS-specific branch.";
            case JVM:
                return "Use Android/Desktop targets or replace with a COMMON-compatible block.";
            case NATIVE:
                return "Use a Native target or isolate this block in platform-specific flow.";
            case ALL_EXCEPT_IOS:
                return "Exclude iOS target for this flow or choose an iOS-compatible alternative.";
            case COMMON:
            default:
                return "Consider replacing this block with a target-compatible alternative.";
        }
    }
}
