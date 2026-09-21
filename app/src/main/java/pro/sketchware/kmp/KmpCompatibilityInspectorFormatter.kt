package pro.sketchware.kmp

class KmpCompatibilityInspectorSummary(
    @JvmField val title: String,
    @JvmField val message: String,
    @JvmField val warningCount: Int,
    @JvmField val uniqueWarningCount: Int,
    @JvmField val quickFixes: List<String>
)

object KmpCompatibilityInspectorFormatter {
    @JvmStatic
    fun build(targetLabel: String, warningMessages: List<String>): KmpCompatibilityInspectorSummary {
        val normalizedTarget = targetLabel.trim().ifEmpty { "Auto" }
        val groupedWarnings = linkedMapOf<String, Int>()
        for (message in warningMessages) {
            val normalized = message.trim()
            if (normalized.isEmpty()) {
                continue
            }
            groupedWarnings[normalized] = (groupedWarnings[normalized] ?: 0) + 1
        }

        val quickFixes = listOf(
            "Switch target context",
            "Open multi-target preview"
        )

        if (groupedWarnings.isEmpty()) {
            return KmpCompatibilityInspectorSummary(
                "KMP compatibility inspector",
                "Target context: $normalizedTarget\nNo compatibility warnings for the selected target.",
                0,
                0,
                quickFixes
            )
        }

        val messageBuilder = StringBuilder()
        messageBuilder.append("Target context: ").append(normalizedTarget).append('\n')
            .append("Warnings: ").append(warningMessages.size).append('\n').append('\n')

        var index = 1
        for ((warning, count) in groupedWarnings) {
            messageBuilder.append(index)
                .append(". ")
                .append(warning)
            if (count > 1) {
                messageBuilder.append(" (x").append(count).append(')')
            }
            messageBuilder.append('\n')
            index++
        }

        messageBuilder.append('\n')
            .append("Quick fixes:")
            .append('\n')
            .append("- ")
            .append(quickFixes[0])
            .append('\n')
            .append("- ")
            .append(quickFixes[1])

        return KmpCompatibilityInspectorSummary(
            "KMP compatibility inspector",
            messageBuilder.toString(),
            warningMessages.size,
            groupedWarnings.size,
            quickFixes
        )
    }
}