package com.nofar.core.ffi

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.nofar_ffi.coreApiVersion

@RunWith(AndroidJUnit4::class)
class NofarFfiAndroidTest {
    @Test
    fun loadsBionicLibraryAndReadsVersion() {
        CoreVersionHandshake.verify()
        assertThat(coreApiVersion().toInt()).isEqualTo(1)
    }
}
