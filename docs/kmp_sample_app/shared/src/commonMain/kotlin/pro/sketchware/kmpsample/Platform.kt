package pro.sketchware.kmpsample

object Platform {
    fun name(): String = GeneratedPlatformBindings.platformName()

    fun sampleDigest(): String {
        val platform = name()
        GeneratedPlatformBindings.logInfo("KMP-Sample", "Running on $platform")
        GeneratedPlatformBindings.putKeyValue("sample.platform", platform)
        val persisted = GeneratedPlatformBindings.getKeyValue("sample.platform")
        return "$persisted@${GeneratedPlatformBindings.currentTimeMillis()}"
    }
}
