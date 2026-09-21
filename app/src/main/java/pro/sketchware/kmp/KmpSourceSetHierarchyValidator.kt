package pro.sketchware.kmp

object KmpSourceSetHierarchyValidator {
    class Diagnostic(
        @JvmField val code: String,
        @JvmField val path: String,
        @JvmField val message: String
    )

    class Result(
        @JvmField val valid: Boolean,
        @JvmField val diagnostics: List<Diagnostic>
    ) {
        fun hasErrors(): Boolean {
            return diagnostics.isNotEmpty()
        }
    }

    @JvmStatic
    fun validate(hierarchy: Map<String, List<String>>?): Result {
        val diagnostics = mutableListOf<Diagnostic>()
        if (hierarchy == null || hierarchy.isEmpty()) {
            diagnostics.add(Diagnostic(
                "HIERARCHY_EMPTY",
                "sourceSetHierarchy",
                "Source set hierarchy must not be empty"
            ))
            return Result(false, diagnostics)
        }

        if (!hierarchy.containsKey("commonMain")) {
            diagnostics.add(Diagnostic(
                "MISSING_COMMON_MAIN",
                "sourceSetHierarchy.commonMain",
                "commonMain source set is required"
            ))
        }

        val nodeNames = hierarchy.keys
        for ((sourceSet, parents) in hierarchy) {
            if (sourceSet.trim().isEmpty()) {
                diagnostics.add(Diagnostic(
                    "INVALID_SOURCE_SET_NAME",
                    "sourceSetHierarchy",
                    "Source set name must not be blank"
                ))
                continue
            }

            for ((index, parentRaw) in parents.withIndex()) {
                val parent = parentRaw.trim()
                if (parent.isEmpty()) {
                    diagnostics.add(Diagnostic(
                        "INVALID_PARENT_NAME",
                        "sourceSetHierarchy.$sourceSet[$index]",
                        "Parent source set name must not be blank"
                    ))
                    continue
                }

                if (parent == sourceSet) {
                    diagnostics.add(Diagnostic(
                        "SELF_DEPENDENCY",
                        "sourceSetHierarchy.$sourceSet[$index]",
                        "Source set must not depend on itself"
                    ))
                    continue
                }

                if (!nodeNames.contains(parent)) {
                    diagnostics.add(Diagnostic(
                        "MISSING_PARENT",
                        "sourceSetHierarchy.$sourceSet[$index]",
                        "Parent source set '$parent' is not declared"
                    ))
                }
            }
        }

        val state = mutableMapOf<String, Int>()
        for (node in hierarchy.keys) {
            state[node] = 0
        }

        fun dfs(node: String, stack: MutableList<String>) {
            state[node] = 1
            stack.add(node)

            val parents = hierarchy[node] ?: emptyList()
            for (parentRaw in parents) {
                val parent = parentRaw.trim()
                if (parent.isEmpty() || !hierarchy.containsKey(parent)) {
                    continue
                }

                val parentState = state[parent] ?: 0
                if (parentState == 0) {
                    dfs(parent, stack)
                } else if (parentState == 1) {
                    diagnostics.add(Diagnostic(
                        "CYCLE_DETECTED",
                        "sourceSetHierarchy.$node",
                        "Cycle detected in source set hierarchy: ${stack.joinToString(" -> ")} -> $parent"
                    ))
                }
            }

            stack.removeAt(stack.size - 1)
            state[node] = 2
        }

        for (node in hierarchy.keys) {
            if (state[node] == 0) {
                dfs(node, mutableListOf())
            }
        }

        return Result(diagnostics.isEmpty(), diagnostics)
    }
}
