package com.nofar.core.model

enum class OsmType {
    NODE,
    WAY,
    RELATION;

    companion object {
        fun fromTag(tag: String): OsmType? = entries.find { it.name.equals(tag, ignoreCase = true) }
    }
}
