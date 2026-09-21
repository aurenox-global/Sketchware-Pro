package pro.sketchware.blocks.typing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TypedBlockRegistry {

    private final ConcurrentMap<String, TypedBlockSignature> byOpcode = new ConcurrentHashMap<>();

    public TypedBlockSignature register(TypedBlockSignature signature) {
        if (signature == null || signature.opcode.isEmpty()) {
            return null;
        }
        byOpcode.put(signature.opcode, signature);
        return signature;
    }

    public TypedBlockSignature registerLegacy(String opcode,
                                              String typeToken,
                                              String typeName,
                                              String spec) {
        return register(TypedBlockSignature.fromLegacy(opcode, typeToken, typeName, spec));
    }

    public TypedBlockSignature registerLegacy(String opcode,
                                              String typeToken,
                                              String typeName,
                                              String spec,
                                              TypedBlockScope scope) {
        TypedBlockSignature inferred = TypedBlockSignature.fromLegacy(opcode, typeToken, typeName, spec);
        TypedBlockScope safeScope = scope == null ? inferred.scope : scope;
        return register(new TypedBlockSignature(
                inferred.opcode,
                safeScope,
                inferred.kind,
                inferred.returnType,
                inferred.typeToken,
                inferred.typeName,
                inferred.spec,
                inferred.inputs
        ));
    }

    public TypedBlockSignature find(String opcode) {
        if (opcode == null || opcode.isEmpty()) {
            return null;
        }
        return byOpcode.get(opcode);
    }

    public List<TypedBlockSignature> snapshot() {
        ArrayList<TypedBlockSignature> signatures = new ArrayList<>(byOpcode.values());
        signatures.sort(Comparator.comparing(signature -> signature.opcode));
        return Collections.unmodifiableList(signatures);
    }

    public void clear() {
        byOpcode.clear();
    }
}
