package com.nofar.core.model

enum class AppLanguage {
    SYSTEM,
    ENGLISH,
    HEBREW
    ;

    val storageValue: String
        get() =
            when (this) {
                SYSTEM -> "system"
                ENGLISH -> "en"
                HEBREW -> "iw"
            }

    companion object {
        fun fromStorageValue(value: String?): AppLanguage = when (value) {
            "en" -> ENGLISH
            "iw", "he" -> HEBREW
            else -> SYSTEM
        }

        fun fromLocaleTag(tag: String): AppLanguage = when {
            tag.startsWith("en") -> ENGLISH
            tag == "iw" || tag == "he" -> HEBREW
            else -> SYSTEM
        }
    }
}
