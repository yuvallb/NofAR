package com.nofar.core.data.osm

import com.nofar.core.model.LabelLanguage

object OsmNameResolver {
    fun resolveCanonicalName(tags: Map<String, String>): String? = tags["name"]?.takeIf { it.isNotBlank() }

    fun resolveDisplayName(tags: Map<String, String>, language: LabelLanguage): String? {
        val localized =
            language.osmNameTag
                ?.let { tag -> tags[tag]?.takeIf { it.isNotBlank() } }
        val international =
            if (language != LabelLanguage.DEFAULT) {
                tags["int_name"]?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        return localized ?: international ?: resolveCanonicalName(tags)
    }
}
