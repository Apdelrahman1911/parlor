package com.parlor.networking.security

import java.security.MessageDigest

internal actual fun platformSha256(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(input)
