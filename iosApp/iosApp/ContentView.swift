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
        ZStack {
            ComposeView()
                .ignoresSafeArea()
                .accessibilityHidden(scenePhase != .active)

            // iOS snapshots the scene for the app switcher after it becomes
            // inactive. Cover roles and private actions before that snapshot,
            // but do not suspend the LAN session until true background.
            if scenePhase != .active {
                Color.black
                    .ignoresSafeArea()
                    .accessibilityHidden(true)
            }
        }
        .onAppear {
            reportScenePhase(scenePhase)
        }
        .onChange(of: scenePhase) { phase in
            reportScenePhase(phase)
        }
    }

    private func reportScenePhase(_ phase: ScenePhase) {
        switch phase {
        case .active:
            MainViewControllerKt.NotifyAppForegrounded()
        case .inactive:
            MainViewControllerKt.NotifyAppInactive()
        case .background:
            MainViewControllerKt.NotifyAppBackgrounded()
        @unknown default:
            // An unknown state is privacy-covered but is not evidence that the
            // process entered background, so keep transport state unchanged.
            MainViewControllerKt.NotifyAppInactive()
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
