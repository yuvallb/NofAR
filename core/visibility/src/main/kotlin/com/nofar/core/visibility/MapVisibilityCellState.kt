package com.nofar.core.visibility

/** Per-cell visibility in the virtual-location map viewshed. */
enum class MapVisibilityCellState(val code: Byte) {
    VISIBLE(0),
    BLOCKED(1),
    UNKNOWN(2)
    ;

    companion object {
        fun fromCode(code: Byte): MapVisibilityCellState = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
