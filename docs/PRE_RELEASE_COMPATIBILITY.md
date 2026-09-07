# Pre-release Compatibility Policy

Parlor is unpublished. This policy applies to internal development/testing
builds; it is not a promise of production migration support or final content
approval. Release migrations must be specified before a published format or
behavior changes.

## iOS language ownership

Supported behavior starts with a clean install and language changes made by
the current ownership-aware build. English/Arabic selections may install an
app-domain `AppleLanguages` override. Selecting System releases only an exact
override owned by Parlor, restoring the prior preference or prior absence.
The ownership record survives a normal process restart. OS-managed preferences
remain authoritative when Parlor cannot establish ownership.

Ambiguous unmarked overrides left by old internal builds have **no supported
migration path**. The app does not guess their origin or silently delete them.
This also avoids deleting an indistinguishable legitimate iOS per-app language
preference. System follows the effective OS language preference; it does not
promise to override the user's iOS per-app language choice.

For development state that needs resetting, use a **fresh, explicitly owned
test simulator/profile**. Dispose of only that owned profile after preserving
required evidence. Do not reset a connected personal device, erase an existing
user profile, delete global Apple language preferences, or indiscriminately
remove app-domain preferences. A fresh profile intentionally starts without
prior test settings/saves; there is no automatic in-app reset or migration.

UserDefaults persistence is asynchronous. Tested normal restarts do not promise
atomic cross-process preference transactions or durability after sudden power
loss. Clean/current-build Settings, direction and lifecycle checks remain
required; adopting this policy does not make unexecuted checks pass.

## Bundled testing content and saved sessions

Corrected testing stories receive a new content version. Content identity
includes the canonical story text, not just its version string. An unchanged
save format or protocol version does not make two different story revisions
interchangeable.

Local recovery and LAN admission require exact content/version/digest matching.
Whodunit saves missing a content identity cannot launch: matching character or
clue references cannot prove which story prose created them. Existing codecs
may still decode legacy records for inspection; decoding is not permission to
resume them. No save-format or protocol migration is introduced.

Old internal test saves have **no backward migration requirement**. If content
is incompatible or its identity is absent, recovery explains the failure and
allows an explicit discard/restart; it must not rewrite a snapshot, automatically
delete the save, or pretend old and new content have the same identity. Retired
Solo and other unsupported local-mode records are likewise rejected before
payload decoding and kept until explicit discard, not migrated to Pass-and-Play.
Players in a room must use matching content. Protocol compatibility remains
exact 4.2.

See [Whodunit testing-content decisions](WHODUNIT_TEST_CONTENT.md) for the
individual corrections, versions and editorial reasoning. All testing stories
still require a separate full editorial/content-rights review before production.
