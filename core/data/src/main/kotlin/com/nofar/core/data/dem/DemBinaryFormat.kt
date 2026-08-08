package com.nofar.core.data.dem

object DemBinaryFormat {
    /**
     * V3 invalidates rasters produced before TIFF floating-point Predictor=3 decoding was supported.
     * Those rasters contained mostly invalid elevations even when all source tiles were present.
     */
    const val MAGIC = "NOFAR_DM3"
    const val MAGIC_SIZE_BYTES = 9
    const val HEADER_SIZE_BYTES = MAGIC_SIZE_BYTES + 4 + 4 + 8 + 8 + 4
    const val DEFAULT_NO_DATA_VALUE = -9999.0f
}
