package com.nofar.core.ui

import androidx.annotation.StringRes
import com.nofar.core.model.LabelLanguage

@StringRes
fun LabelLanguage.labelStringRes(): Int = when (this) {
    LabelLanguage.DEFAULT -> R.string.label_language_default
    LabelLanguage.HEBREW -> R.string.label_language_hebrew
    LabelLanguage.ARABIC -> R.string.label_language_arabic
    LabelLanguage.ENGLISH -> R.string.label_language_english
    LabelLanguage.RUSSIAN -> R.string.label_language_russian
}
