package pro.sketchware.kmp

import java.util.Locale

class KmpDependencyAlternativeSuggestion(
    @JvmField val legacyNormalizedCoordinate: String,
    @JvmField val alternatives: List<String>,
    @JvmField val rationale: String,
    @JvmField val actionId: String
)

object KmpDependencyAlternativeSuggestionCatalog {
    const val ACTION_OPEN_KMP_DEPENDENCY_ALTERNATIVES = "open_kmp_dependency_alternatives"

    private val SUGGESTIONS = linkedMapOf(
        "com.squareup.retrofit2:retrofit" to KmpDependencyAlternativeSuggestion(
            "com.squareup.retrofit2:retrofit",
            listOf(
                "io.ktor:ktor-client-core",
                "io.ktor:ktor-client-cio",
                "io.ktor:ktor-client-darwin"
            ),
            "Prefer Ktor clients with per-target engines to keep networking code in commonMain.",
            ACTION_OPEN_KMP_DEPENDENCY_ALTERNATIVES
        ),
        "androidx.room:room-runtime" to KmpDependencyAlternativeSuggestion(
            "androidx.room:room-runtime",
            listOf(
                "app.cash.sqldelight:runtime",
                "io.realm.kotlin:library-base"
            ),
            "Prefer multiplatform persistence libraries when shared data access is required.",
            ACTION_OPEN_KMP_DEPENDENCY_ALTERNATIVES
        ),
        "com.google.code.gson:gson" to KmpDependencyAlternativeSuggestion(
            "com.google.code.gson:gson",
            listOf(
                "org.jetbrains.kotlinx:kotlinx-serialization-json",
                "io.ktor:ktor-serialization-kotlinx-json"
            ),
            "Prefer Kotlin serialization for shared models and payload parsing.",
            ACTION_OPEN_KMP_DEPENDENCY_ALTERNATIVES
        ),
        "io.reactivex.rxjava3:rxjava" to KmpDependencyAlternativeSuggestion(
            "io.reactivex.rxjava3:rxjava",
            listOf(
                "org.jetbrains.kotlinx:kotlinx-coroutines-core",
                "kotlinx.coroutines.flow"
            ),
            "Prefer coroutines and Flow to keep async pipelines compatible across KMP targets.",
            ACTION_OPEN_KMP_DEPENDENCY_ALTERNATIVES
        )
    )

    @JvmStatic
    fun findSuggestion(normalizedCoordinate: String): KmpDependencyAlternativeSuggestion? {
        val normalized = normalizedCoordinate.trim().lowercase(Locale.ROOT)
        return SUGGESTIONS[normalized]
    }

    @JvmStatic
    fun buildSuggestionText(normalizedCoordinate: String): String? {
        val suggestion = findSuggestion(normalizedCoordinate) ?: return null
        val alternatives = suggestion.alternatives.joinToString(", ")
        return "Suggested KMP alternatives: $alternatives. ${suggestion.rationale}"
    }
}