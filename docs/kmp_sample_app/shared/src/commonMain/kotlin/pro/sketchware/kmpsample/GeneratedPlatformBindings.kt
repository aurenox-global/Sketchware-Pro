package pro.sketchware.kmpsample

expect object GeneratedPlatformBindings {
    fun platformName(): String
    fun logInfo(tag: String, message: String): Unit
    fun putKeyValue(key: String, value: String): Unit
    fun getKeyValue(key: String): String
    fun currentTimeMillis(): Long
}
