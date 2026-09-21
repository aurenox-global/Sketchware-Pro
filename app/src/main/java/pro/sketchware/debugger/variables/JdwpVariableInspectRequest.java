package pro.sketchware.debugger.variables;

public final class JdwpVariableInspectRequest {

    public final String sessionId;
    public final long threadId;
    public final int frameIndex;
    public final String parentVariableId;
    public final boolean includeStaticFields;
    public final boolean includeInheritedFields;
    public final int maxDepth;
    public final int maxChildren;

    public JdwpVariableInspectRequest(String sessionId,
                                      long threadId,
                                      int frameIndex,
                                      String parentVariableId,
                                      boolean includeStaticFields,
                                      boolean includeInheritedFields,
                                      int maxDepth,
                                      int maxChildren) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.threadId = Math.max(threadId, 0L);
        this.frameIndex = Math.max(frameIndex, 0);
        this.parentVariableId = parentVariableId == null ? "" : parentVariableId;
        this.includeStaticFields = includeStaticFields;
        this.includeInheritedFields = includeInheritedFields;
        this.maxDepth = Math.max(maxDepth, 1);
        this.maxChildren = Math.max(maxChildren, 1);
    }

    public static JdwpVariableInspectRequest forFrame(String sessionId, long threadId, int frameIndex) {
        return new JdwpVariableInspectRequest(
                sessionId,
                threadId,
                frameIndex,
                "",
                true,
                true,
                1,
                120
        );
    }

    public JdwpVariableInspectRequest forChildren(String parentVariableId) {
        return new JdwpVariableInspectRequest(
                sessionId,
                threadId,
                frameIndex,
                parentVariableId,
                includeStaticFields,
                includeInheritedFields,
                maxDepth,
                maxChildren
        );
    }
}
