package pro.sketchware.kmp

import com.google.gson.GsonBuilder
import java.util.Locale

object KmpDependencyCompatibilityReportSerializer {
    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    @JvmStatic
    fun toJson(result: KmpDependencyCompatibilityResult): String {
        return gson.toJson(result)
    }

    @JvmStatic
    fun toLogLine(result: KmpDependencyCompatibilityResult): String {
        val compatible = result.entries.count { it.status == KmpDependencyCompatibilityStatus.COMPATIBLE }
        val partiallyCompatible = result.entries.count { it.status == KmpDependencyCompatibilityStatus.PARTIALLY_COMPATIBLE }
        val incompatible = result.entries.count { it.status == KmpDependencyCompatibilityStatus.INCOMPATIBLE }
        val unknown = result.entries.count { it.status == KmpDependencyCompatibilityStatus.UNKNOWN }

        return String.format(
            Locale.US,
            "KMP dependency compatibility: compatible=%d partiallyCompatible=%d incompatible=%d unknown=%d total=%d",
            compatible,
            partiallyCompatible,
            incompatible,
            unknown,
            result.entries.size
        )
    }
}