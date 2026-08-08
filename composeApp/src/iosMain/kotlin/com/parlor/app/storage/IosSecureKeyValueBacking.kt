@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.parlor.app.storage

import com.parlor.storage.secure.SecureKeyValueBacking
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
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
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Device-only, non-synchronizing iOS Keychain backing for small credentials.
 *
 * `AfterFirstUnlockThisDeviceOnly` matches Parlor's explicit short background
 * recovery policy while preventing iCloud Keychain synchronization or restore
 * onto another device. No secret is mirrored into preferences or app files.
 */
internal class IosSecureKeyValueBacking(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SecureKeyValueBacking {

    override suspend fun put(key: String, value: ByteArray) = withContext(dispatcher) {
        requireValidKey(key)
        require(value.size <= MAX_VALUE_BYTES) { "Secure value exceeds $MAX_VALUE_BYTES bytes" }
        updateOrAdd(key, value)
    }

    override suspend fun get(key: String): ByteArray? = withContext(dispatcher) {
        requireValidKey(key)
        read(key)
    }

    override suspend fun remove(key: String) = withContext(dispatcher) {
        requireValidKey(key)
        withQuery(key) { query ->
            when (SecItemDelete(query)) {
                errSecSuccess, errSecItemNotFound -> Unit
                else -> error("Keychain credential delete failed")
            }
        }
    }

    private fun read(key: String): ByteArray? =
        withQuery(key = key, returnData = true) { query ->
            memScoped {
                val result = alloc<CFTypeRefVar>()
                result.value = null
                val status = SecItemCopyMatching(query, result.ptr)
                val value = result.value
                try {
                    when (status) {
                        errSecItemNotFound -> null
                        errSecSuccess -> {
                            val returned = value ?: error("Keychain returned no credential data")
                            check(CFGetTypeID(returned) == CFDataGetTypeID()) {
                                "Keychain returned invalid credential data"
                            }
                            copyData(returned.reinterpret())
                        }
                        else -> error("Keychain credential read failed")
                    }
                } finally {
                    value?.let(::CFRelease)
                }
            }
        }

    private fun updateOrAdd(key: String, value: ByteArray) {
        val updated = withQuery(key) { query ->
            withValueAttributes(value) { attributes ->
                SecItemUpdate(query, attributes)
            }
        }
        when (updated) {
            errSecSuccess -> return
            errSecItemNotFound -> Unit
            else -> error("Keychain credential update failed")
        }

        val added = withQuery(key = key, valueData = value) { query ->
            SecItemAdd(query, null)
        }
        when (added) {
            errSecSuccess -> Unit
            errSecDuplicateItem -> {
                // A concurrent first writer won. Update the durable item with
                // this transaction's latest complete record.
                val retry = withQuery(key) { query ->
                    withValueAttributes(value) { attributes ->
                        SecItemUpdate(query, attributes)
                    }
                }
                check(retry == errSecSuccess) { "Keychain credential retry failed" }
            }
            else -> error("Keychain credential add failed")
        }
    }

    private inline fun <T> withQuery(
        key: String,
        valueData: ByteArray? = null,
        returnData: Boolean = false,
        block: (CFDictionaryRef) -> T,
    ): T {
        val dictionary = newDictionary()
        val service = createString(KEYCHAIN_SERVICE)
        val account = createString(key)
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
                data = createData(valueData)
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

    private inline fun <T> withValueAttributes(
        value: ByteArray,
        block: (CFDictionaryRef) -> T,
    ): T {
        val dictionary = newDictionary()
        val data = createData(value)
        try {
            CFDictionarySetValue(dictionary, kSecValueData, data)
            CFDictionarySetValue(
                dictionary,
                kSecAttrAccessible,
                kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )
            return block(dictionary)
        } finally {
            CFRelease(data)
            CFRelease(dictionary)
        }
    }

    private fun newDictionary(): CFDictionaryRef = CFDictionaryCreateMutable(
        allocator = kCFAllocatorDefault,
        capacity = 0,
        keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
        valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
    ) ?: error("Couldn't allocate Keychain query")

    private fun createString(value: String) = CFStringCreateWithCString(
        alloc = kCFAllocatorDefault,
        cStr = value,
        encoding = kCFStringEncodingUTF8,
    ) ?: error("Couldn't encode Keychain credential key")

    private fun createData(bytes: ByteArray): CFDataRef =
        bytes.usePinned { pinned ->
            CFDataCreate(
                allocator = kCFAllocatorDefault,
                bytes = pinned.addressOf(0).reinterpret(),
                length = bytes.size.toLong(),
            )
        } ?: error("Couldn't allocate Keychain credential data")

    private fun copyData(data: CFDataRef): ByteArray {
        val size = CFDataGetLength(data)
        check(size in 0..MAX_VALUE_BYTES.toLong()) { "Keychain credential is oversized" }
        if (size == 0L) return ByteArray(0)
        return ByteArray(size.toInt()).also { output ->
            output.usePinned { pinned ->
                CFDataGetBytes(
                    data,
                    CFRangeMake(0, size),
                    pinned.addressOf(0).reinterpret(),
                )
            }
        }
    }

    private fun requireValidKey(key: String) {
        require(key.length in 1..MAX_KEY_LENGTH && key.all(::isSafeKeyCharacter)) {
            "Secure-storage key is invalid"
        }
    }

    private fun isSafeKeyCharacter(character: Char): Boolean =
        character.isLetterOrDigit() || character == '.' || character == '_' || character == '-'

    private companion object {
        const val KEYCHAIN_SERVICE = "com.parlor.app.resumable-session.v1"
        const val MAX_KEY_LENGTH = 64
        const val MAX_VALUE_BYTES = 16 * 1024
    }
}
