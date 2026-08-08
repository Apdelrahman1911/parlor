@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.parlor.networking.security

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

internal actual fun platformSha256(input: ByteArray): ByteArray {
    val output = ByteArray(CC_SHA256_DIGEST_LENGTH)
    input.usePinned { inputPinned ->
        output.usePinned { outputPinned ->
            CC_SHA256(
                inputPinned.addressOf(0),
                input.size.convert(),
                outputPinned.addressOf(0).reinterpret(),
            )
        }
    }
    return output
}
