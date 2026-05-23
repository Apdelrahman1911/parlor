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
        fun parse(s: String): SemVer {
            val parts = s.trim().split(".")
            require(parts.size in 1..3) { "Invalid SemVer: '$s'" }
            val major = parts.getOrNull(0)?.toIntOrNull() ?: error("Invalid major in '$s'")
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            return SemVer(major, minor, patch)
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
