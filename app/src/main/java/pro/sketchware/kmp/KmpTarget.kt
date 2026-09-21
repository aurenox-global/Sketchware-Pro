package pro.sketchware.kmp

enum class KmpTarget(@JvmField val sourceSetName: String) {
    ANDROID("androidMain"),
    DESKTOP("desktopMain"),
    WASM_JS("wasmJsMain"),
    IOS_ARM64("iosMain"),
    IOS_SIMULATOR_ARM64("iosMain"),
    IOS_X64("iosMain"),
    LINUX_X64("linuxX64Main");

    companion object {
        @JvmStatic
        fun fromId(value: String?): KmpTarget? {
            if (value == null) {
                return null
            }

            val normalized = value
                .trim()
                .replace('-', '_')
                .replace(' ', '_')
                .uppercase()

            if (normalized.isEmpty()) {
                return null
            }

            return entries.firstOrNull { it.name == normalized }
        }
    }
}
