import NofARCoreBridge
import SwiftUI

@main
struct NofARApp: App {
    @State private var bootMessage = "Loading core…"

    var body: some Scene {
        WindowGroup {
            ContentView(message: bootMessage)
                .task {
                    do {
                        try CoreVersionHandshake.verify()
                        bootMessage = "NofAR core OK (API v1)"
                    } catch {
                        bootMessage = "Core unavailable: \(error)"
                    }
                }
        }
    }
}
