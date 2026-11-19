# Supplementary display-name format controls

## Confirmed boundary defect

[#293](https://github.com/Apdelrahman1911/parlor/issues/293) concerns
`shared/networking/.../room/RoomInputPolicy.kt`, consumed by name setup and
authoritative admission/rejoin validation. Accepted main `d566a576` checks
`Char.category == FORMAT` on UTF-16 units. Supplementary format scalars are valid
surrogate pairs; neither unit is FORMAT. The well-formed-pair check does not
validate their scalar category, and sanitization copies them unchanged.

Construct LANGUAGE TAG U+E0001 as `"\uDB40\uDC01"`. Original validation accepts
that scalar alone or after `Alice`, and roster validation accepts both `Alice`
and the format-suffixed label. Sanitizing31 A characters, the tag and B loses B
because the unfiltered pair exceeds the32-unit capacity. These are actual policy
results, not evidence of native keyboard failure or physical rendering.

## Scoped correction

Decode each well-formed surrogate pair before checking the127 supplementary
FORMAT scalars in [UnicodeData17.0.0](https://www.unicode.org/Public/17.0.0/ucd/UnicodeData.txt).
Reviewed source SHA256
`2e1efc1dcb59c575eedf5ccae60f95229f706ee6d031835247d843c11d96470c`.
The finite common-Kotlin ranges require no platform API, networking, new
dependency or per-input allocation. Validation rejects them; UI sanitization
discards them before spending capacity. BMP controls/format rejection,
well-formed surrogate checks,32-unit limit and trimming remain unchanged.

Preserve Arabic/international text, supplementary letters, emoji and variation
marks. Names are still exact/case-sensitive labels, not identities. Closed
[#169](https://github.com/Apdelrahman1911/parlor/issues/169) rejects the separate
proposal for confusable/case/space normalization; this fix does not reopen that
decision. Host authority and authenticated PlayerId remain unchanged. Do not
silently normalize an invalid stored label into another player's seat.

## Executed regression evidence

Strict wrapper8.13/JDK21/Desktop execution against original policy SHA256
`f4fe96788fbb709ac7e9a1d2b66a010b124d4456e5779272527f2392f43bd118`:
eight methods, two exact new assertion failures; no compile error or skip.
Corrected policy passes all eight, including all127 scalars, retained B,
Arabic, supplementary letters/emoji/marks and existing `Alice`/`alice` equality.
Original/corrected XML SHA256:
`bf8f97c0ea170bd7927c5b1e38a71d919166c1ede0c4a89c02b9209d38bd1cd0` /
`acc02341440e462aa4c122e3b0bcb94cf886e83efd75a0cacb7820acf64117c1`.
One3GiB worker/private daemon registry, owned stop0. No runtime Unicode download.

This confirms policy rejection/sanitization, not physical appearance,
end-to-end admission, native input delivery, signing or production acceptance.
Broader affected/final-source and required hosted checks remain necessary before
protected integration and issue closure. #267/#276 and physical/Store gates stay
separate. No paid infrastructure, extra agents, weakened tests or release.
