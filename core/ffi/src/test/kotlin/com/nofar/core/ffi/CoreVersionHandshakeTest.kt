package com.nofar.core.ffi

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uniffi.nofar_ffi.CorePaths
import uniffi.nofar_ffi.coreApiVersion
import uniffi.nofar_ffi.uniffiBindingsVersion

class CoreVersionHandshakeTest {
    @Test
    fun verify_succeedsWhenBindingsMatch() {
        CoreVersionHandshake.verify()
        assertThat(coreApiVersion().toInt()).isEqualTo(1)
        assertThat(uniffiBindingsVersion().toInt()).isEqualTo(1)
    }

    @Test
    fun openComputeOnly_acceptsValidPaths() {
        val core =
            NofarCoreFactory.openComputeOnly(
                CorePaths(
                    databaseFile = "/data/data/com.nofar.app/databases/nofar.db",
                    demRoot = "/data/data/com.nofar.app/files/dem",
                    stagingRoot = "/data/data/com.nofar.app/files/staging",
                    tempRoot = "/data/data/com.nofar.app/cache/nofar-temp"
                )
            )
        assertThat(core.coreApiVersion().toInt()).isEqualTo(1)
        assertThat(core.supportedDemFormatVersions()).containsExactly(1u)
        core.close()
    }
}
