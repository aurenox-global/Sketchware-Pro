package pro.sketchware.kmp

enum class KmpBuildPipelineMode {
    ANDROID_COMPATIBILITY,
    KMP_GRADLE_EXPERIMENTAL
}

class KmpBuildPipelineResolution(
    @JvmField val mode: KmpBuildPipelineMode,
    @JvmField val reason: String
)

object KmpBuildPipelineResolver {
    @JvmStatic
    fun resolve(
        kmpMetadataDetected: Boolean,
        kmpGradleBridgeEnabled: Boolean,
        kmpGradleWrapperAvailable: Boolean
    ): KmpBuildPipelineResolution {
        if (!kmpMetadataDetected) {
            return KmpBuildPipelineResolution(
                KmpBuildPipelineMode.ANDROID_COMPATIBILITY,
                "KMP metadata not detected"
            )
        }

        if (!kmpGradleBridgeEnabled) {
            return KmpBuildPipelineResolution(
                KmpBuildPipelineMode.ANDROID_COMPATIBILITY,
                "KMP Gradle bridge disabled by build setting"
            )
        }

        if (!kmpGradleWrapperAvailable) {
            return KmpBuildPipelineResolution(
                KmpBuildPipelineMode.ANDROID_COMPATIBILITY,
                "KMP Gradle wrapper not found"
            )
        }

        return KmpBuildPipelineResolution(
            KmpBuildPipelineMode.KMP_GRADLE_EXPERIMENTAL,
            "KMP metadata detected and Gradle bridge available"
        )
    }
}
