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
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .onChange(of: scenePhase) { phase in
                switch phase {
                case .active:
                    MainViewControllerKt.NotifyAppForegrounded()
                case .inactive, .background:
                    MainViewControllerKt.NotifyAppBackgrounded()
                @unknown default:
                    MainViewControllerKt.NotifyAppBackgrounded()
                }
            }
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
