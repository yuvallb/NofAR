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
            "iw" -> HEBREW
            else -> SYSTEM
        }
    }
}
