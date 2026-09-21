package pro.sketchware.blocks.typing;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TypedBlockTypeChecker {

    private final TypedBlockRegistry registry;

    public TypedBlockTypeChecker() {
        this(new TypedBlockRegistry());
    }

    public TypedBlockTypeChecker(TypedBlockRegistry registry) {
        this.registry = registry == null ? new TypedBlockRegistry() : registry;
    }

    public TypedBlockTypeCheckResult check(List<BlockBean> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return TypedBlockTypeCheckResult.empty();
        }

        ArrayList<TypedBlockTypeDiagnostic> diagnostics = new ArrayList<>();
        Map<String, BlockBean> blocksById = new HashMap<>();
        Map<String, TypedBlockSignature> signaturesById = new HashMap<>();

        for (BlockBean block : blocks) {
            if (block == null) {
                continue;
            }

            String blockId = normalize(block.id);
            if (!blockId.isEmpty()) {
                if (blocksById.containsKey(blockId)) {
                    diagnostics.add(new TypedBlockTypeDiagnostic(
                            TypedBlockTypeDiagnosticCode.DUPLICATE_BLOCK_ID,
                            TypedBlockTypeDiagnosticSeverity.WARNING,
                            blockId,
                            normalize(block.opCode),
                            -1,
                            "",
                            TypedBlockValueType.UNKNOWN,
                            TypedBlockValueType.UNKNOWN,
                            "",
                            "Duplicate block id detected: " + blockId
                    ));
                } else {
                    blocksById.put(blockId, block);
                }

                signaturesById.put(blockId, resolveSignature(block));
            }
        }

        for (BlockBean block : blocks) {
            if (block == null) {
                continue;
            }

            String blockId = normalize(block.id);
            TypedBlockSignature signature = blockId.isEmpty()
                    ? resolveSignature(block)
                    : signaturesById.get(blockId);
            if (signature == null) {
                continue;
            }

            int expectedInputs = signature.inputs.size();
            int actualInputs = block.parameters == null ? 0 : block.parameters.size();

            if (actualInputs < expectedInputs) {
                diagnostics.add(new TypedBlockTypeDiagnostic(
                        TypedBlockTypeDiagnosticCode.MISSING_ARGUMENTS,
                        TypedBlockTypeDiagnosticSeverity.WARNING,
                        blockId,
                        signature.opcode,
                        -1,
                        "",
                        TypedBlockValueType.UNKNOWN,
                        TypedBlockValueType.UNKNOWN,
                        "",
                        "Block expects " + expectedInputs + " argument(s) but found " + actualInputs
                ));
            } else if (actualInputs > expectedInputs) {
                diagnostics.add(new TypedBlockTypeDiagnostic(
                        TypedBlockTypeDiagnosticCode.EXTRA_ARGUMENTS,
                        TypedBlockTypeDiagnosticSeverity.WARNING,
                        blockId,
                        signature.opcode,
                        -1,
                        "",
                        TypedBlockValueType.UNKNOWN,
                        TypedBlockValueType.UNKNOWN,
                        "",
                        "Block has " + actualInputs + " argument(s) but only " + expectedInputs + " expected"
                ));
            }

            int checkedInputs = Math.min(expectedInputs, actualInputs);
            for (int index = 0; index < checkedInputs; index++) {
                TypedBlockInputPort input = signature.inputAt(index);
                if (input == null) {
                    continue;
                }

                String parameter = normalize(block.parameters.get(index));
                if (!isReference(parameter)) {
                    continue;
                }

                String referencedId = parameter.substring(1);
                BlockBean referencedBlock = blocksById.get(referencedId);
                if (referencedBlock == null) {
                    diagnostics.add(new TypedBlockTypeDiagnostic(
                            TypedBlockTypeDiagnosticCode.INVALID_REFERENCE,
                            TypedBlockTypeDiagnosticSeverity.ERROR,
                            blockId,
                            signature.opcode,
                            index,
                            input.name,
                            input.expectedType,
                            TypedBlockValueType.UNKNOWN,
                            referencedId,
                            "Input references missing block @" + referencedId
                    ));
                    continue;
                }

                TypedBlockSignature referencedSignature = signaturesById.get(referencedId);
                TypedBlockValueType actualType = referencedSignature == null
                        ? TypedBlockValueType.UNKNOWN
                        : referencedSignature.returnType;

                TypedBlockValueType expectedType = input.expectedType;
                if (expectedType == TypedBlockValueType.UNKNOWN || expectedType == TypedBlockValueType.ANY) {
                    continue;
                }

                if (!expectedType.isAssignableFrom(actualType)) {
                    diagnostics.add(new TypedBlockTypeDiagnostic(
                            TypedBlockTypeDiagnosticCode.INPUT_TYPE_MISMATCH,
                            TypedBlockTypeDiagnosticSeverity.ERROR,
                            blockId,
                            signature.opcode,
                            index,
                            input.name,
                            expectedType,
                            actualType,
                            referencedId,
                            "Input type mismatch at index " + index + ": expected "
                                    + expectedType.id + " but found " + actualType.id
                    ));
                }
            }
        }

        return new TypedBlockTypeCheckResult(blocks.size(), System.currentTimeMillis(), diagnostics);
    }

    public TypedBlockTypeCheckResult check(List<BlockBean> blocks, Collection<String> enabledTargetIds) {
        TypedBlockTypeCheckResult baseResult = check(blocks);
        if (blocks == null || blocks.isEmpty()) {
            return baseResult;
        }

        List<TypedBlockTypeDiagnostic> compatibilityDiagnostics =
                new TypedBlockTargetCompatibilityValidator(registry).validate(blocks, enabledTargetIds);
        if (compatibilityDiagnostics.isEmpty()) {
            return baseResult;
        }

        ArrayList<TypedBlockTypeDiagnostic> merged = new ArrayList<>(baseResult.diagnostics.size() + compatibilityDiagnostics.size());
        merged.addAll(baseResult.diagnostics);
        merged.addAll(compatibilityDiagnostics);
        return new TypedBlockTypeCheckResult(baseResult.analyzedBlockCount, System.currentTimeMillis(), merged);
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

    private static boolean isReference(String parameter) {
        return parameter != null && parameter.length() > 1 && parameter.charAt(0) == '@';
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
