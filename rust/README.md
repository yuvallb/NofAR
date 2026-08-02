# NofAR Rust workspace (MP0+)

Pinned toolchain: see `rust-toolchain.toml`.

## Common commands

```bash
cd rust
cargo test --all
cargo fmt --all
cargo clippy --all-targets -- -D warnings
cargo deny check

# Generate Kotlin bindings (also run automatically by Gradle :core:ffi preBuild)
cargo run -p xtask -- kotlin-bindings --out-dir ../core/ffi/src/generated/kotlin

# Host library for JVM unit tests
cargo run -p xtask -- host-lib

# Android shared libraries (requires ANDROID_NDK_HOME, cargo-ndk)
cargo run -p xtask -- android

# iOS XCFramework + Swift bindings (macOS + Xcode)
cargo run -p xtask -- ios
```

Gradle wires `generateUniffiBindings`, `cargoBuildHost`, and `cargoBuildAndroid` via the `nofar.rust` convention plugin on `:core:ffi`.
