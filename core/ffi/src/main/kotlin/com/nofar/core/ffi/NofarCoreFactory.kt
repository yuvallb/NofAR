package com.nofar.core.ffi

import uniffi.nofar_ffi.CoreMode
import uniffi.nofar_ffi.CorePaths
import uniffi.nofar_ffi.NofarCore

/**
 * Opens [NofarCore] in compute-only mode for MP0–MP3 (no live SQLite until MP4b).
 */
object NofarCoreFactory {
    fun openComputeOnly(paths: CorePaths): NofarCore {
        CoreVersionHandshake.verify()
        return NofarCore(paths, CoreMode.COMPUTE_ONLY)
    }
}
