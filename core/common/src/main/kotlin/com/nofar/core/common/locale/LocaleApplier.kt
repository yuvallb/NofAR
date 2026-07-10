package com.nofar.core.common.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.nofar.core.model.AppLanguage

object LocaleApplier {
    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(language.toLocaleList())
    }

    fun getCurrentLanguage(): AppLanguage = fromLocaleList(AppCompatDelegate.getApplicationLocales())

    fun fromLocaleList(locales: LocaleListCompat): AppLanguage {
        if (locales.isEmpty) return AppLanguage.SYSTEM
        return AppLanguage.fromLocaleTag(locales[0]?.toLanguageTag().orEmpty())
    }

    private fun AppLanguage.toLocaleList(): LocaleListCompat = when (this) {
        AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
        AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
        AppLanguage.HEBREW -> LocaleListCompat.forLanguageTags("iw")
    }
}
