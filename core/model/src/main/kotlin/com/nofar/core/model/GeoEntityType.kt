package com.nofar.core.model

enum class GeoEntityType {
    CITY,
    TOWN,
    VILLAGE,
    HAMLET,
    ISOLATED_DWELLING,
    LOCALITY,
    PEAK;

    fun matchesResolution(level: ResolutionLevel): Boolean = when (this) {
        PEAK -> level.includesPeaks
        else -> name.lowercase() in level.placeTags
    }

    companion object {
        fun fromTag(tag: String): GeoEntityType? = entries.find { it.name.equals(tag, ignoreCase = true) }
    }
}
