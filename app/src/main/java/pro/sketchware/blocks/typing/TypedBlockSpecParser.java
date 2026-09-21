package pro.sketchware.blocks.typing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TypedBlockSpecParser {

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("%([a-zA-Z])(?:\\.([a-zA-Z0-9_]+))?");

    private TypedBlockSpecParser() {
    }

    public static List<TypedBlockInputPort> parse(String spec) {
        if (spec == null || spec.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<TypedBlockInputPort> ports = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(spec);
        int index = 0;

        while (matcher.find()) {
            String token = matcher.group(1);
            if ("t".equals(token)) {
                continue;
            }

            String typeName = matcher.group(2);
            String safeTypeName = typeName == null ? "" : typeName.trim();
            String portName = safeTypeName.isEmpty() ? "arg" + (index + 1) : safeTypeName;

            StringBuilder rawToken = new StringBuilder("%").append(token);
            if (!safeTypeName.isEmpty()) {
                rawToken.append('.').append(safeTypeName);
            }

            ports.add(new TypedBlockInputPort(
                    index,
                    portName,
                    TypedBlockValueType.fromLegacyPlaceholder(token, safeTypeName),
                    rawToken.toString(),
                    safeTypeName
            ));
            index++;
        }

        return Collections.unmodifiableList(ports);
    }
}
