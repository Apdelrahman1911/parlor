"""Pinned host-only sampler copy; never changes the simulator sampler.

H01 (34546624383) observed ordered [macOS, Mac Catalyst] on Foundation and
CoreFoundation: only the old singleton predicate failed. No PAC change.
"""
import hashlib


SAMPLER_SHA256 = '3f4f49af81d396a07beabeed45be649314495fd286327baa931aa5eb2dcca137'
ORIGINAL_PLATFORM = b'platforms.count == 1'
HOST_PLATFORM = b'''([platforms isEqualToArray:@[@(PLATFORM_MACOS)]] ||
               [platforms isEqualToArray:@[@(PLATFORM_MACOS), @(PLATFORM_MACCATALYST)]])'''
ORIGINAL_GUARD = b'''    demand(offset == header->sizeofcmds && uuid && platforms.count == 1 && executableAddress &&
           (uintptr_t)address >= (uintptr_t)header, @"image-executable-address");'''
PREAMBLE = b'''
// H01 supports exactly this ordered host platform policy, not arbitrary pairs.
#if !TARGET_OS_OSX || TARGET_OS_SIMULATOR || TARGET_OS_MACCATALYST || !defined(__arm64__)
#error The copied host sampler requires native arm64 macOS, never a simulator.
#endif
#if PLATFORM_MACOS != 1 || PLATFORM_MACCATALYST != 6
#error Host platform constants differ from the observed public SDK identities.
#endif
'''


def require(condition, reason):
    if not condition:
        raise RuntimeError('protection-host-sampler-' + reason)


def transform(source: bytes) -> bytes:
    """Admit only [macOS] or observed [macOS, Catalyst] in a native-host copy."""
    require(type(source) is bytes and hashlib.sha256(source).hexdigest() == SAMPLER_SHA256, 'source-pin')
    anchor = b'#include <unistd.h>\n'
    require(source.count(anchor) == source.count(ORIGINAL_GUARD) == source.count(ORIGINAL_PLATFORM) == 1,
            'copy-anchor')
    guard = ORIGINAL_GUARD.replace(ORIGINAL_PLATFORM, HOST_PLATFORM)
    copied = source.replace(anchor, anchor + PREAMBLE).replace(ORIGINAL_GUARD, guard)
    require(copied.replace(PREAMBLE, b'').replace(HOST_PLATFORM, ORIGINAL_PLATFORM) == source and
            copied.count(guard) == 1, 'unrelated-source-change')
    return copied
