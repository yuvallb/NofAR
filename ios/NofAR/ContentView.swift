import SwiftUI

struct ContentView: View {
    let message: String

    var body: some View {
        VStack(spacing: 16) {
            Text("NofAR")
                .font(.largeTitle)
            Text(message)
                .multilineTextAlignment(.center)
                .padding()
        }
    }
}

#Preview {
    ContentView(message: "Preview")
}
