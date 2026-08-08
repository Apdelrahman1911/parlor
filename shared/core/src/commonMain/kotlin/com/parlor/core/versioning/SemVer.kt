package com.parlor.core.versioning

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Semver-like dotted integer version: `"1.2.3"`. Used for content versions,
 * minimum app version, snapshot versions.
 *
 * Comparison is component-wise. Extra trailing components default to zero.
 */
@Serializable(with = SemVer.Serializer::class)
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /**
         * Strict parse. Every present component must be a non-negative integer;
         * absent trailing components default to 0 (so "1" → 1.0.0, "1.2" → 1.2.0).
         * Malformed ("1.x", "1.2.beta", "1.", "1..3"), negative ("-1.0.0"), or
         * over-long inputs throw [IllegalArgumentException]. This matters because
         * untrusted content version strings (CaseEnvelope.version / minimumAppVersion)
         * deserialize through here; the content validator maps the thrown
         * IllegalArgumentException to a MalformedField error instead of silently
         * coercing garbage to a low version that would slip past the
         * AppUpdateRequired gate. See PROBLEMS_PARLOR.md → core-002/003.
         */
        fun parse(s: String): SemVer {
            val parts = s.trim().split(".")
            require(parts.size in 1..3) { "Invalid SemVer: '$s'" }
            fun component(index: Int, name: String): Int {
                if (index >= parts.size) return 0
                val value = parts[index].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid $name in SemVer '$s'")
                require(value >= 0) { "Negative $name in SemVer '$s'" }
                return value
            }
            return SemVer(component(0, "major"), component(1, "minor"), component(2, "patch"))
        }

        val ZERO = SemVer(0, 0, 0)
    }

    object Serializer : KSerializer<SemVer> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("SemVer", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: SemVer) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): SemVer = parse(decoder.decodeString())
    }
}
