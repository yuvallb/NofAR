package com.nofar.core.ffi

import uniffi.nofar_ffi.coreApiVersion
import uniffi.nofar_ffi.uniffiBindingsVersion

/**
 * Validates that generated UniFFI bindings were produced from the same
 * [EXPECTED_UNIFFI_BINDINGS_VERSION] as this module expects (MP0 handshake).
 */
object CoreVersionHandshake {
    const val EXPECTED_UNIFFI_BINDINGS_VERSION: Int = 1

    fun verify() {
        val fromCore = uniffiBindingsVersion()
        require(fromCore == EXPECTED_UNIFFI_BINDINGS_VERSION.toUInt()) {
            "UniFFI bindings version mismatch: expected $EXPECTED_UNIFFI_BINDINGS_VERSION, got $fromCore"
        }
        val api = coreApiVersion()
        require(api == EXPECTED_UNIFFI_BINDINGS_VERSION.toUInt()) {
            "Core API version mismatch: expected $EXPECTED_UNIFFI_BINDINGS_VERSION, got $api"
        }
    }
}
