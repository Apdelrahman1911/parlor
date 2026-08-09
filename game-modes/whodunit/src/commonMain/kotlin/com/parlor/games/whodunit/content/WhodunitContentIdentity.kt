package com.parlor.games.whodunit.content

import com.parlor.content.schema.CaseEnvelope
import com.parlor.core.versioning.SemVer
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.security.SecureHashes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Exact, non-secret identity of the validated content used by one session. */
data class WhodunitContentIdentity(
    val version: String,
    val digest: String,
) {
    init {
        require(version.isValidSemVer()) {
            "Invalid Whodunit content version"
        }
        require(digest.length == SHA_256_HEX_LENGTH && digest.all { it in HEX }) {
            "Invalid Whodunit content digest"
        }
    }
}

private fun String.isValidSemVer(): Boolean = try {
    SemVer.parse(this)
    true
} catch (_: Exception) {
    false
}

/**
 * Produces a stable SHA-256 identity for gameplay-visible case content.
 * Object keys are recursively sorted so insignificant JSON key ordering does
 * not split a room. The delivery signature is excluded: it authenticates the
 * envelope source but is not game content and may differ between a bundled
 * fallback and an otherwise identical remote envelope.
 */
fun CaseEnvelope.contentIdentity(): WhodunitContentIdentity {
    val unsignedEnvelope = copy(signature = null)
    val encoded = IDENTITY_JSON.encodeToJsonElement(
        CaseEnvelope.serializer(),
        unsignedEnvelope,
    )
    val canonical = IDENTITY_JSON.encodeToString(
        JsonElement.serializer(),
        encoded.canonicalized(),
    ).encodeToByteArray()
    val digest = try {
        SecureHashes.sha256(canonical).toLowerHex()
    } finally {
        canonical.fill(0)
    }
    return WhodunitContentIdentity(
        version = version.toString(),
        digest = digest,
    )
}

/** Requires the peer's independently loaded case to match the host exactly. */
fun HostMessage.SessionStarting.matches(case: CaseEnvelope): Boolean {
    val expected = case.contentIdentity()
    return caseVersion == expected.version && caseDigest == expected.digest
}

private fun JsonElement.canonicalized(): JsonElement = when (this) {
    is JsonArray -> JsonArray(map(JsonElement::canonicalized))
    is JsonObject -> JsonObject(
        entries.sortedBy(Map.Entry<String, JsonElement>::key)
            .associate { (key, value) -> key to value.canonicalized() },
    )
    else -> this
}

private fun ByteArray.toLowerHex(): String = buildString(size * HEX_CHARACTERS_PER_BYTE) {
    for (byte in this@toLowerHex) {
        val value = byte.toInt() and UNSIGNED_BYTE_MASK
        append(HEX[value ushr HALF_BYTE_BITS])
        append(HEX[value and HALF_BYTE_MASK])
    }
}

private val IDENTITY_JSON = Json {
    encodeDefaults = true
    explicitNulls = true
}
private const val HEX = "0123456789abcdef"
private const val SHA_256_HEX_LENGTH = 64
private const val HEX_CHARACTERS_PER_BYTE = 2
private const val UNSIGNED_BYTE_MASK = 0xff
private const val HALF_BYTE_BITS = 4
private const val HALF_BYTE_MASK = 0x0f
