@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

// Read-only numeric corroboration in the same app, AFTER production results.
// Not instrumentation of the original production query. Never creates a key/item.
package com.parlor.app.audit

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemCopyMatching
import platform.Security.errSecItemNotFound
import platform.Security.errSecMissingEntitlement
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData

internal fun subsequentSameAppKeychainRead(): JsonObject {
    val query = CFDictionaryCreateMutable(
        kCFAllocatorDefault, 0,
        kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
    ) ?: throw AuditAbort("native_query_allocation_failed")
    try {
        val service = CFStringCreateWithCString(
            kCFAllocatorDefault, "com.parlor.app.resumable-session.v1", kCFStringEncodingUTF8,
        ) ?: throw AuditAbort("native_service_allocation_failed")
        try {
            val account = CFStringCreateWithCString(
                kCFAllocatorDefault, "p2p-resumable-session-v1", kCFStringEncodingUTF8,
            ) ?: throw AuditAbort("native_account_allocation_failed")
            try {
                CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
                CFDictionarySetValue(query, kSecAttrService, service)
                CFDictionarySetValue(query, kSecAttrAccount, account)
                CFDictionarySetValue(query, kSecAttrSynchronizable, kCFBooleanFalse)
                CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
                CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
                return memScoped {
                    val result = alloc<CFTypeRefVar>()
                    result.value = null
                    val status = SecItemCopyMatching(query, result.ptr)
                    val value = result.value
                    try {
                        if (value != null) throw AuditAbort("unexpected_native_value")
                        buildJsonObject {
                            put("origin", "subsequent_same_app_equivalent_read")
                            put("original_production_status", false)
                            put("os_status", status)
                            put("result_present", false)
                            put("constant_not_found", errSecItemNotFound)
                            put("constant_missing_entitlement", errSecMissingEntitlement)
                        }
                    } finally {
                        value?.let(::CFRelease)
                    }
                }
            } finally {
                CFRelease(account)
            }
        } finally {
            CFRelease(service)
        }
    } finally {
        CFRelease(query)
    }
}
