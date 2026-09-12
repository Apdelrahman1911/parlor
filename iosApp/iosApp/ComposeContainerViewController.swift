import UIKit

/// Gives SwiftUI its own outer view while Compose owns the child's semantics.
/// Host updates must not compete with the language provider's explicit native
/// direction on the Compose view. Navigation and session ownership stay in Compose.
final class ComposeContainerViewController: UIViewController {
    private let contentController: UIViewController

    init(contentController: UIViewController) {
        self.contentController = contentController
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("Create this container with its content controller")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(contentController)
        let contentView = contentController.view!
        contentView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentView)
        // Full physical bounds, not directional or safe-area anchors: parent
        // and child languages may differ, and Compose already handles insets.
        NSLayoutConstraint.activate([
            contentView.leftAnchor.constraint(equalTo: view.leftAnchor),
            contentView.rightAnchor.constraint(equalTo: view.rightAnchor),
            contentView.topAnchor.constraint(equalTo: view.topAnchor),
            contentView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        contentController.didMove(toParent: self)
    }

    override var childForStatusBarStyle: UIViewController? { contentController }
    override var childForStatusBarHidden: UIViewController? { contentController }
    override var childForHomeIndicatorAutoHidden: UIViewController? { contentController }
    override var childForScreenEdgesDeferringSystemGestures: UIViewController? { contentController }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        contentController.supportedInterfaceOrientations
    }

    override var preferredInterfaceOrientationForPresentation: UIInterfaceOrientation {
        contentController.preferredInterfaceOrientationForPresentation
    }
}
