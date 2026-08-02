// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "NofARCoreBridge",
    platforms: [.iOS(.v18)],
    products: [
        .library(name: "NofARCoreBridge", targets: ["NofARCoreBridge"]),
    ],
    targets: [
        .target(
            name: "NofARCoreBridge",
            path: "Sources/NofARCoreBridge",
            swiftSettings: [
                .enableExperimentalFeature("StrictConcurrency"),
            ]
        ),
        .testTarget(
            name: "NofARCoreBridgeTests",
            dependencies: ["NofARCoreBridge"],
            path: "Tests/NofARCoreBridgeTests"
        ),
    ]
)
