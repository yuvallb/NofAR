# iOS app (MP0 skeleton)

Minimum iOS **18**. From a clean checkout:

```bash
cd rust
cargo run -p xtask -- ios    # Swift bindings + XCFramework (requires Xcode)
open ../ios/NofAR.xcodeproj
```

The Xcode project links the local `NofARCoreBridge` Swift package. Generated UniFFI output lives under
`NofARCoreBridge/Sources/NofARCoreBridge/Generated/` and is not committed.

Until `xtask ios` has been run, `CoreVersionHandshake` uses stub version checks so the shell UI still builds.
