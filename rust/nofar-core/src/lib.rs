//! Pure NofAR domain and algorithms (no UniFFI types).

/// Increment when the exported FFI contract changes incompatibly.
pub const CORE_API_VERSION: u32 = 1;

/// Must match generated Kotlin/Swift binding snapshots (see `CoreVersionHandshake`).
pub const UNIFFI_BINDINGS_VERSION: u32 = 1;

pub mod model;
