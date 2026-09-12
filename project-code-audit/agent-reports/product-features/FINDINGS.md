# Product-features findings (lead)

### PF-001 — Solo is a visible non-mode
- Severity: Medium / Confidence: High
- Same as UI-001. `GameEntryMode.Solo` exists; both shipping bindings omit it;
  picker still draws the card disabled.

### PF-002 — Android LAN needs no dangerous permission; INTERNET still declared
- Severity: Informational / Confidence: High
- `P2pPermissionGate` Android path is `NotRequired`. Manifest still has
  INTERNET + WIFI_STATE + CHANGE_WIFI_MULTICAST_STATE. No Nearby/Location.
  `usesCleartextTraffic=false`.

### PF-003 — iOS Local Network cannot be preflighted
- Severity: Low / Confidence: High
- Gate models Unknown until a real advertise/browse. Empty discovery is not
  treated as denial. Needs device confirmation of Settings recovery copy.
