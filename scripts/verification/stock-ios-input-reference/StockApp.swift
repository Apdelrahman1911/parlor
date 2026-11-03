import UIKit

// Standalone reference, never linked to Parlor or its observer adapter.
@main
final class StockAppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = StockInputController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}

final class StockInputController: UIViewController, UITextViewDelegate {
    private let field = UITextView()
    private let status = UILabel()
    private var requested = false
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        field.delegate = self
        field.accessibilityIdentifier = "stock-input"
        field.font = .systemFont(ofSize: 17)
        field.layer.borderWidth = 1
        field.layer.borderColor = UIColor.label.cgColor
        field.isScrollEnabled = false
        field.textContainer.maximumNumberOfLines = 1
        field.textContainer.lineBreakMode = .byClipping
        field.keyboardType = .default
        field.keyboardAppearance = .default
        field.returnKeyType = .done
        field.autocapitalizationType = .sentences
        field.autocorrectionType = .yes
        field.isSecureTextEntry = false
        field.textContentType = nil
        field.enablesReturnKeyAutomatically = false
        field.translatesAutoresizingMaskIntoConstraints = false
        let request = UIButton(type: .system)
        request.setTitle("Read failed native state", for: .normal)
        request.accessibilityIdentifier = "stock-failure-request"
        request.addTarget(self, action: #selector(readFailure), for: .touchUpInside)
        request.translatesAutoresizingMaskIntoConstraints = false
        status.accessibilityIdentifier = "stock-native-state"
        status.text = "unrequested"
        status.translatesAutoresizingMaskIntoConstraints = false
        [field, request, status].forEach { view.addSubview($0) }
        NSLayoutConstraint.activate([
            field.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 100),
            field.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            field.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            field.heightAnchor.constraint(equalToConstant: 100),
            request.topAnchor.constraint(equalTo: field.bottomAnchor, constant: 16),
            request.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            status.topAnchor.constraint(equalTo: request.bottomAnchor, constant: 8),
            status.centerXAnchor.constraint(equalTo: view.centerXAnchor)
        ])
    }
    func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool {
        guard text == "\n" else { return true }
        textView.resignFirstResponder() // Only the user/XCTest Done event; no draft mutation.
        return false
    }
    @objc private func readFailure() {
        guard !requested else { return }
        requested = true
        let text = field.text ?? ""
        let value = text.isEmpty ? 0 : (text == "Table Test" ? 1 : 2)
        let frame = field.convert(field.bounds, to: view.window)
        let valid = !frame.isNull && !frame.isInfinite && !frame.isEmpty &&
            [frame.origin.x, frame.origin.y, frame.size.width, frame.size.height, frame.maxX, frame.maxY]
                .allSatisfy { $0.isFinite } && frame.size.width > 0 && frame.size.height > 0
        status.text = "schema=1,value=\(value),focus=\(field.isFirstResponder ? 1 : 0)," +
            "frame=\(valid ? 1 : 0),editable=\(field.isEditable ? 1 : 0),enabled=\(field.isUserInteractionEnabled ? 1 : 0)"
    }
}
