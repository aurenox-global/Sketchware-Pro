package pro.sketchware.kmpsample

actual object GeneratedPlatformBindings {
    actual fun platformName(): String = "Desktop"
    actual fun logInfo(tag: String, message: String): Unit = println("[$tag] $message")
    actual fun putKeyValue(key: String, value: String): Unit = run { System.setProperty(key, value); Unit }
    actual fun getKeyValue(key: String): String = System.getProperty(key).orEmpty()
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
}
