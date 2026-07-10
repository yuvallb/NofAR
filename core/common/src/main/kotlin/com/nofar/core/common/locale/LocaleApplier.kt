package com.nofar.core.common.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.nofar.core.model.AppLanguage

object LocaleApplier {
    fun apply(language: AppLanguage) {
        val locales =
            when (language) {
                AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
                AppLanguage.HEBREW -> LocaleListCompat.forLanguageTags("iw")
            }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
