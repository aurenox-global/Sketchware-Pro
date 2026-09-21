package pro.sketchware.kmp

class KmpPreviewSnapshot(
    @JvmField val sharedSnapshot: String,
    @JvmField val androidPreview: String,
    @JvmField val desktopPreview: String,
    @JvmField val webPlaceholder: String,
    @JvmField val iosPlaceholder: String
)

object KmpPreviewSnapshotBuilder {
    @JvmStatic
    fun build(
        eventName: String?,
        sourceFile: String?,
        blockCount: Int,
        selectedTargetId: String?
    ): KmpPreviewSnapshot {
        val normalizedEvent = eventName?.trim().orEmpty().ifEmpty { "unknown_event" }
        val normalizedSourceFile = sourceFile?.trim().orEmpty().ifEmpty { "unknown_source" }
        val normalizedTarget = selectedTargetId?.trim().orEmpty().ifEmpty { "AUTO" }
        val safeBlockCount = if (blockCount < 0) 0 else blockCount

        val sharedSnapshot = "event=$normalizedEvent source=$normalizedSourceFile blocks=$safeBlockCount target=$normalizedTarget"
        return KmpPreviewSnapshot(
            sharedSnapshot,
            "Android snapshot: $sharedSnapshot",
            "Desktop snapshot: $sharedSnapshot",
            "Web preview placeholder: renderer pending (snapshot available above).",
            "iOS preview placeholder: renderer pending (snapshot available above)."
        )
    }
}