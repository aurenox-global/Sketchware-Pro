package pro.sketchware.kmp

import com.besome.sketch.beans.BlockBean
import pro.sketchware.blocks.typing.TypedBlockTypeCheckResult
import pro.sketchware.blocks.typing.TypedBlockTypeChecker

object KmpBlockCompatibilityValidator {
    @JvmStatic
    fun validate(blocks: List<BlockBean>?, project: KmpProject?): TypedBlockTypeCheckResult {
        return validate(blocks, project, null)
    }

    @JvmStatic
    fun validate(
        blocks: List<BlockBean>?,
        project: KmpProject?,
        selectedTarget: KmpTarget?
    ): TypedBlockTypeCheckResult {
        if (blocks == null || blocks.isEmpty()) {
            return TypedBlockTypeCheckResult.empty()
        }

        val enabledTargetIds = if (selectedTarget != null) {
            listOf(selectedTarget.name)
        } else {
            project?.enabledTargets
                ?.map { it.name }
                ?: emptyList()
        }

        return TypedBlockTypeChecker().check(blocks, enabledTargetIds)
    }
}
