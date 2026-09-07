package com.parlor.games.mafia.ui.screens.night

import com.parlor.core.ids.PlayerId
import com.parlor.games.mafia.domain.settings.MafiaSettings

/** Shared by both Mafia UIs, after each router has selected living, active seats. */
internal fun isDoctorTargetEligible(
    target: PlayerId,
    doctor: PlayerId,
    previousProtection: PlayerId?,
    settings: MafiaSettings,
): Boolean =
    (settings.doctorCanSelfHeal || target != doctor) &&
        (settings.doctorCanProtectSamePlayerConsecutively || target != previousProtection)
