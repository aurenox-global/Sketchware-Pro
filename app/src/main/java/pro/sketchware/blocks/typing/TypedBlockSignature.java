package pro.sketchware.blocks.typing;

import java.util.Collections;
import java.util.List;

public final class TypedBlockSignature {

    public final String opcode;
    public final TypedBlockScope scope;
    public final TypedBlockKind kind;
    public final TypedBlockValueType returnType;
    public final String typeToken;
    public final String typeName;
    public final String spec;
    public final List<TypedBlockInputPort> inputs;

    public TypedBlockSignature(String opcode,
                               TypedBlockKind kind,
                               TypedBlockValueType returnType,
                               String typeToken,
                               String typeName,
                               String spec,
                               List<TypedBlockInputPort> inputs) {
        this(opcode, TypedBlockScope.COMMON, kind, returnType, typeToken, typeName, spec, inputs);
    }

    public TypedBlockSignature(String opcode,
                               TypedBlockScope scope,
                               TypedBlockKind kind,
                               TypedBlockValueType returnType,
                               String typeToken,
                               String typeName,
                               String spec,
                               List<TypedBlockInputPort> inputs) {
        this.opcode = opcode == null ? "" : opcode;
        this.scope = scope == null ? TypedBlockScope.COMMON : scope;
        this.kind = kind == null ? TypedBlockKind.UNKNOWN : kind;
        this.returnType = returnType == null ? TypedBlockValueType.UNKNOWN : returnType;
        this.typeToken = typeToken == null ? "" : typeToken;
        this.typeName = typeName == null ? "" : typeName;
        this.spec = spec == null ? "" : spec;
        this.inputs = inputs == null ? Collections.emptyList() : inputs;
    }

    public static TypedBlockSignature fromLegacy(String opcode,
                                                 String typeToken,
                                                 String typeName,
                                                 String spec) {
        return new TypedBlockSignature(
                opcode,
            TypedBlockScope.inferFromLegacy(opcode, typeName, spec),
                TypedBlockKind.fromLegacyTypeToken(typeToken),
                TypedBlockValueType.fromLegacyBlockType(typeToken, typeName),
                typeToken,
                typeName,
                spec,
                TypedBlockSpecParser.parse(spec)
        );
    }

    public TypedBlockInputPort inputAt(int index) {
        if (index < 0 || index >= inputs.size()) {
            return null;
        }
        return inputs.get(index);
    }

    public boolean acceptsInput(int index, TypedBlockValueType actualType) {
        TypedBlockInputPort port = inputAt(index);
        if (port == null) {
            return false;
        }
        return port.expectedType.isAssignableFrom(actualType);
    }
}
