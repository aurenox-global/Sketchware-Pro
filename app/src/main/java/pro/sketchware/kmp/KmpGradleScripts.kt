package pro.sketchware.kmp

class KmpGradleScripts(
    @JvmField val settingsGradleKts: String,
    @JvmField val rootBuildGradleKts: String,
    @JvmField val gradleProperties: String,
    @JvmField val sharedBuildGradleKts: String,
    @JvmField val androidAppBuildGradleKts: String,
    @JvmField val desktopAppBuildGradleKts: String,
    @JvmField val versionCatalogToml: String
)
