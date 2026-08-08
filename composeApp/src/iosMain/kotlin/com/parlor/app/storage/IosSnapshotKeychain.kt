@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.parlor.app.storage

import com.parlor.storage.snapshot.SnapshotProtectionException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytes
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataGetTypeID
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFGetTypeID
import platform.CoreFoundation.CFRangeMake
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Stores only the random snapshot encryption/MAC keys in a device-only,
 * non-synchronizing Keychain item. Snapshot ciphertext remains in Application
 * Support and is excluded from backup.
 */
internal class IosSnapshotKeychain {

    fun readExisting(): ByteArray? = withQuery(returnData = true) { query ->
        memScoped {
            val result = alloc<CFTypeRefVar>()
            result.value = null
            val status = SecItemCopyMatching(query, result.ptr)
            val value = result.value
            try {
                when (status) {
                    errSecItemNotFound -> null
                    errSecSuccess -> {
                        val returned = value
                            ?: throw IllegalStateException("Keychain returned no snapshot key")
                        if (CFGetTypeID(returned) != CFDataGetTypeID()) {
                            throw SnapshotProtectionException()
                        }
                        copyKeyData(returned.reinterpret())
                    }
                    else -> throw IllegalStateException("Keychain snapshot-key read failed")
                }
            } finally {
                value?.let(::CFRelease)
            }
        }
    }

    /**
     * Creates once and then re-reads the durable winner. This is safe when two
     * app tasks race during first launch: a duplicate add never replaces the
     * key that may already protect a snapshot.
     */
    fun loadOrCreate(): ByteArray {
        readExisting()?.let { return it }

        val candidate = secureRandomBytes(KEY_BYTES)
        try {
            withQuery(valueData = candidate) { query ->
                when (SecItemAdd(query, null)) {
                    errSecSuccess,
                    errSecDuplicateItem -> Unit
                    else -> throw IllegalStateException("Keychain snapshot-key add failed")
                }
            }
        } finally {
            candidate.fill(0)
        }
        return readExisting()
            ?: throw IllegalStateException("Snapshot key was not durable after Keychain add")
    }

    private inline fun <T> withQuery(
        valueData: ByteArray? = null,
        returnData: Boolean = false,
        block: (CFDictionaryRef) -> T,
    ): T {
        val dictionary = CFDictionaryCreateMutable(
            allocator = kCFAllocatorDefault,
            capacity = 0,
            keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
            valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: throw IllegalStateException("Couldn't allocate Keychain query")
        val service = CFStringCreateWithCString(
            alloc = kCFAllocatorDefault,
            cStr = KEYCHAIN_SERVICE,
            encoding = kCFStringEncodingUTF8,
        ) ?: run {
            CFRelease(dictionary)
            throw IllegalStateException("Couldn't encode Keychain service")
        }
        val account = CFStringCreateWithCString(
            alloc = kCFAllocatorDefault,
            cStr = KEYCHAIN_ACCOUNT,
            encoding = kCFStringEncodingUTF8,
        ) ?: run {
            CFRelease(service)
            CFRelease(dictionary)
            throw IllegalStateException("Couldn't encode Keychain account")
        }
        var data: CFDataRef? = null
        try {
            CFDictionarySetValue(dictionary, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(dictionary, kSecAttrService, service)
            CFDictionarySetValue(dictionary, kSecAttrAccount, account)
            CFDictionarySetValue(dictionary, kSecAttrSynchronizable, kCFBooleanFalse)
            if (returnData) {
                CFDictionarySetValue(dictionary, kSecReturnData, kCFBooleanTrue)
                CFDictionarySetValue(dictionary, kSecMatchLimit, kSecMatchLimitOne)
            }
            if (valueData != null) {
                data = createCfData(valueData)
                CFDictionarySetValue(dictionary, kSecValueData, data)
                CFDictionarySetValue(
                    dictionary,
                    kSecAttrAccessible,
                    kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                )
            }
            return block(dictionary)
        } finally {
            data?.let(::CFRelease)
            CFRelease(account)
            CFRelease(service)
            CFRelease(dictionary)
        }
    }

    private fun createCfData(bytes: ByteArray): CFDataRef =
        bytes.usePinned { pinned ->
            CFDataCreate(
                allocator = kCFAllocatorDefault,
                bytes = pinned.addressOf(0).reinterpret(),
                length = bytes.size.toLong(),
            )
        } ?: throw IllegalStateException("Couldn't allocate Keychain key data")

    private fun copyKeyData(data: CFDataRef): ByteArray {
        val size = CFDataGetLength(data)
        if (size != KEY_BYTES.toLong()) throw SnapshotProtectionException()
        return ByteArray(KEY_BYTES).also { result ->
            result.usePinned { pinned ->
                CFDataGetBytes(
                    data,
                    CFRangeMake(0, size),
                    pinned.addressOf(0).reinterpret(),
                )
            }
        }
    }

    private fun secureRandomBytes(size: Int): ByteArray =
        ByteArray(size).also { bytes ->
            val status = bytes.usePinned { pinned ->
                SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
            }
            if (status != errSecSuccess) {
                bytes.fill(0)
                throw IllegalStateException("Secure random generation failed")
            }
        }

    internal companion object {
        const val KEY_BYTES = 64
        private const val KEYCHAIN_SERVICE = "com.parlor.app.snapshot.v1"
        private const val KEYCHAIN_ACCOUNT = "authenticated-encryption-key"
    }
}
