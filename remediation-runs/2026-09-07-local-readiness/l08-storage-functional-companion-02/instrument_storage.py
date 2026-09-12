"""Copy-only primitive observer at the real credential SecItem return sites.

The coroutine context tags the EXACT probe invocation across dispatcher changes.
Unrelated Home calls are untagged and ignored, not attributed retroactively.
No native result, guard, retry, argument, dictionary, or error mapping changes.
"""

TRACE = 'com.parlor.app.readiness.NativeReadinessInvocation'
CURRENT = 'com.parlor.app.readiness.currentNativeReadinessTrace()'
PATH = 'composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSecureKeyValueBacking.kt'
ADDITION = 'composeApp/src/iosMain/kotlin/com/parlor/app/readiness/NativeReadinessProbe.kt'


def once(source, before, after):
    if source.count(before) != 1:
        raise RuntimeError('Credential observation anchor missing or ambiguous')
    return source.replace(before, after, 1)


def instrument_credentials(source):
    source = once(source, '        updateOrAdd(key, value)',
                  f'        updateOrAdd(key, value, {CURRENT})')
    source = once(source, '        read(key)', f'        read(key, {CURRENT})')
    source = once(source, '            when (SecItemDelete(query)) {',
                  f'            val invocation = {CURRENT}\n'
                  '            val status = SecItemDelete(query)\n'
                  '            invocation?.record("credential-delete", status)\n'
                  '            when (status) {')
    source = once(source, '    private fun read(key: String): ByteArray? =',
                  f'    private fun read(key: String, invocation: {TRACE}?): ByteArray? =')
    source = once(source, '                val status = SecItemCopyMatching(query, result.ptr)',
                  '                val status = SecItemCopyMatching(query, result.ptr)\n'
                  '                invocation?.record("credential-read", status)')
    source = once(source, '    private fun updateOrAdd(key: String, value: ByteArray) {',
                  f'    private fun updateOrAdd(key: String, value: ByteArray, invocation: {TRACE}?) {{')
    source = once(source, '\n                SecItemUpdate(query, attributes)\n',
                  '\n                SecItemUpdate(query, attributes).also { invocation?.record("credential-update", it) }\n')
    source = once(source, '            SecItemAdd(query, null)',
                  '            SecItemAdd(query, null).also { invocation?.record("credential-add", it) }')
    source = once(source, '\n                        SecItemUpdate(query, attributes)\n',
                  '\n                        SecItemUpdate(query, attributes).also { invocation?.record("credential-retry", it) }\n')
    return source
