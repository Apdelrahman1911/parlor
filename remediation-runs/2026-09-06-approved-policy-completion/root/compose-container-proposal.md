# DS-C01 native direction boundary — proposal, not an implemented fix

Source baseline: `de7573ed1f4e1a19969f3063e135d234cd57ec8764c4af784cf4b3eaff5ef9b4`.
Author: `/root`. No application changes or runtime success are asserted here.

`PlatformAppLocale` owns Compose's resolved language and conditionally updates
the actual Compose controller's native semantic direction. The SwiftUI wrapper
currently represents that same controller directly. App-host cycle 02 observed
English/LTR Compose with a forced-RTL native view after foregrounding; the owner
of that overwrite and whether it persists are not yet established.

The v5 witness must retain all observations across a bounded passive window,
use actual Settings before game entry, and fail on disagreement. The original
v4 failure remains evidence; its result must not be relabelled a pass.

If the wrapper boundary is established as the source of interference, consider
one plain UIKit **container**, not a navigation controller: SwiftUI owns the
outer view, and the existing Compose controller owns its stable child view and
language semantics. Use standard child containment, full-bounds layout rather
than safe-area insets, default appearance forwarding, and child forwarding for
status bar/home indicator/system-edge behavior. Do not add a second navigation
stack, a language-keyed root, delays/retry loops, private gesture manipulation,
preference deletion, a second Settings store, or lifecycle-specific reducers.

Planned proof obligations:

- Actual UIKit tests: one child, correct parent/superview, repeated host
  semantic changes cannot overwrite the child's explicit LTR/RTL selection;
  layout/rotation bounds remain full-size; no new controller on updates;
  appearance and system-decoration delegation remain with the child.
- Source-bound Swift/Compose app-host: actual Settings and restart; initial
  real-UI selection, both local games retained through language changes and
  foregrounding; bounded native/Compose agreement; original public Back and
  setup controls still operate.
- Separately classify actual OS Settings interaction and retained multiplayer
  tests. A synthetic store setter is not a user-accessible in-game Settings
  screen or real LAN test.
- Independent full diff/oracle review, applicable focused native/desktop tests,
  and combined verification with strict dependency checking and owned cleanup.

The alternative of reapplying semantics on every foreground callback depends
on undocumented relative ordering with SwiftUI updates and is not a complete
ownership correction. Public SwiftUI-direction bridging is also possible, but
must not create a second competing language owner or leak observer lifetimes.
