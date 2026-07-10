package com.nofar.core.common.text

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

sealed interface UiText {
    data class Dynamic(val value: String) : UiText

    data class Resource(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val resId: Int, val quantity: Int, val args: List<Any> = emptyList()) : UiText

    fun asString(context: Context): String = when (this) {
        is Dynamic -> value
        is Resource -> context.getString(resId, *args.toTypedArray())
        is Plural -> context.resources.getQuantityString(resId, quantity, *args.toTypedArray())
    }
}
