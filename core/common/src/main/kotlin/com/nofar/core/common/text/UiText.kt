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
        is Resource -> context.getString(resId, *sanitizeArgs(args))
        is Plural -> context.resources.getQuantityString(resId, quantity, *sanitizeArgs(args))
    }

    companion object {
        private fun sanitizeArgs(args: List<Any>): Array<Any> = args.map { arg ->
            if (arg is String) {
                arg.replace("%", "%%")
            } else {
                arg
            }
        }.toTypedArray()
    }
}
