@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.parlor.networking.security

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

internal actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "size must be positive" }
    return ByteArray(size).also { bytes ->
        bytes.usePinned { pinned ->
            check(
                SecRandomCopyBytes(
                    kSecRandomDefault,
                    size.toULong(),
                    pinned.addressOf(0),
                ) == errSecSuccess,
            ) {
                "The platform cryptographic random-number generator failed"
            }
        }
    }
}

