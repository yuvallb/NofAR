package com.nofar.core.model

/**
 * Hardcoded resolution levels per Requirements §3.3.1.
 * Not user-facing in MVP — persisted configuration only.
 */
enum class ResolutionLevel(val displayName: String, val placeTags: Set<String>, val includesPeaks: Boolean) {
    Basic(
        displayName = "Basic",
        placeTags = setOf("city"),
        includesPeaks = false
    ),
    Medium(
        displayName = "Medium",
        placeTags = setOf("city", "town", "village"),
        includesPeaks = true
    ),
    Full(
        displayName = "Full",
        placeTags =
        setOf(
            "city",
            "town",
            "village",
            "hamlet",
            "isolated_dwelling",
            "locality"
        ),
        includesPeaks = true
    )
}
