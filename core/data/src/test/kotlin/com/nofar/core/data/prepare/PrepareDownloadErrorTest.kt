package com.nofar.core.data.prepare

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.UnknownHostException
import org.junit.Test

class PrepareDownloadErrorTest {
    @Test
    fun fromThrowable_mapsAllMirrorsFailedMessage() {
        val error =
            PrepareDownloadError.fromThrowable(IOException("All Overpass mirrors failed"))
        assertThat(error).isEqualTo(PrepareDownloadError.AllMirrorsFailed)
        assertThat(error.toUserMessage()).contains("mirrors failed")
    }

    @Test
    fun fromThrowable_mapsUnknownHostToNoNetwork() {
        val error = PrepareDownloadError.fromThrowable(UnknownHostException("host"))
        assertThat(error).isEqualTo(PrepareDownloadError.NoNetwork)
    }

    @Test
    fun partialDemFailure_includesTileCountInMessage() {
        val message = PrepareDownloadError.PartialDemFailure(3).toUserMessage()
        assertThat(message).contains("3")
        assertThat(message).contains("elevation")
    }
}
