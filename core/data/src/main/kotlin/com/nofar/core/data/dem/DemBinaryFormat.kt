package com.nofar.core.data.dem

object DemBinaryFormat {
    const val MAGIC = "NOFAR_DEM"
    const val MAGIC_SIZE_BYTES = 9
    const val HEADER_SIZE_BYTES = MAGIC_SIZE_BYTES + 4 + 4 + 8 + 8 + 4
    const val DEFAULT_NO_DATA_VALUE = -9999.0f
}
