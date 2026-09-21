package pro.sketchware.kmp

object KmpProjectDefaults {
    @JvmField
    val DEFAULT_TARGETS: List<KmpTarget> = listOf(
        KmpTarget.ANDROID,
        KmpTarget.DESKTOP
    )

    @JvmStatic
    fun defaultHierarchy(): Map<String, List<String>> {
        return linkedMapOf(
            "commonMain" to emptyList(),
            "commonTest" to listOf("commonMain"),
            "androidMain" to listOf("commonMain"),
            "desktopMain" to listOf("commonMain"),
            "iosMain" to listOf("commonMain"),
            "nativeMain" to listOf("commonMain"),
            "wasmJsMain" to listOf("commonMain"),
            "jvmMain" to listOf("commonMain")
        )
    }
}
