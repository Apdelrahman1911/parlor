package com.parlor.games.lastlight.ui.flow.multidevice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Constant-space record of interruptions that a conflating UI collector may miss. */
internal class LastLightRecoveryEpoch {
    private val _value = MutableStateFlow(0L)
    val value = _value.asStateFlow()

    fun advance() {
        _value.update { if (it == Long.MAX_VALUE) it else it + 1L }
    }
}

internal fun combinedLastLightPrivacyEpoch(processEpoch: Long, recoveryEpoch: Long): Long =
    processEpoch.coerceAtMost(Long.MAX_VALUE - recoveryEpoch) + recoveryEpoch
