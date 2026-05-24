import SwiftUI
import UIKit
import ComposeApp

/// Hosts the Kotlin Compose Multiplatform root view inside a SwiftUI scene.
///
/// `MainViewController()` is exported from `:composeApp` (see
/// `composeApp/src/iosMain/.../MainViewController.kt`). It builds a
/// `UIViewController` that wraps the shared `App()` composable and
/// starts Koin on first call.
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // No-op — the Kotlin view controller owns its own state.
    }
}
