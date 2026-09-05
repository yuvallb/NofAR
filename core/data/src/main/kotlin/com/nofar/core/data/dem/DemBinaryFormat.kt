package com.nofar.core.data.dem

object DemBinaryFormat {
    /** GLO-90 int16 meters, memory-mapped for Explore. Rejects all prior magic versions. */
    const val MAGIC = "NOFAR_DM4"
    const val MAGIC_SIZE_BYTES = 9
    const val SAMPLE_TYPE_INT16 = 1
    const val SCALE = 1.0f
    const val OFFSET = 0.0f

    /** Stored in the header as float; raster uses [INT16_NO_DATA] as Short. */
    const val HEADER_NO_DATA_VALUE = -32768.0f
    const val INT16_NO_DATA: Short = Short.MIN_VALUE

    const val MIN_QUANTIZED_ELEVATION_M = -1_000
    const val MAX_QUANTIZED_ELEVATION_M = 9_000

    /** magic + width + height + originLat + originLon + sampleType + scale + offset + noData */
    const val HEADER_SIZE_BYTES = MAGIC_SIZE_BYTES + 4 + 4 + 8 + 8 + 4 + 4 + 4 + 4

    const val BYTES_PER_SAMPLE = Short.SIZE_BYTES
}
