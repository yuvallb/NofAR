package com.nofar.core.model

/**
 * Preferred language for OSM feature labels resolved at Prepare/download time.
 *
 * Localized tags use standard OSM colon keys (e.g. `name:he`). [DEFAULT] uses only `name`.
 */
enum class LabelLanguage(val osmNameTag: String?) {
    DEFAULT(null),
    HEBREW("name:he"),
    ARABIC("name:ar"),
    ENGLISH("name:en"),
    RUSSIAN("name:ru");

    companion object {
        fun fromStoredName(name: String): LabelLanguage =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
