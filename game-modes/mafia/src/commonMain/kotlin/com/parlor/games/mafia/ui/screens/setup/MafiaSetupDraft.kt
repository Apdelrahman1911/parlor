package com.parlor.games.mafia.ui.screens.setup

import com.parlor.games.mafia.domain.settings.MafiaKillTie
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.settings.TieBehavior

/** Every rule the shipping setup surface permits the host to configure. */
internal data class MafiaSetupDraft(
    val roleCounts: MafiaRoleCounts,
    val revealRoleOnDeath: Boolean,
    val doctorCanSelfHeal: Boolean,
    val doctorCanProtectSamePlayerConsecutively: Boolean,
    val detectiveCanInspectSelf: Boolean,
    val allowSelfVote: Boolean,
    val mafiaCanTargetMafia: Boolean,
    val voteTieBehavior: TieBehavior,
    val maxRevotes: Int,
    val mafiaKillTieBehavior: MafiaKillTie,
) {
    /**
     * Applies the editable fields while preserving compatibility-only fields
     * that are not enabled by this release (currently the three timers).
     */
    fun applyTo(base: MafiaSettings): MafiaSettings = base.copy(
        roleCounts = roleCounts,
        revealRoleOnDeath = revealRoleOnDeath,
        doctorCanSelfHeal = doctorCanSelfHeal,
        doctorCanProtectSamePlayerConsecutively = doctorCanProtectSamePlayerConsecutively,
        detectiveCanInspectSelf = detectiveCanInspectSelf,
        allowSelfVote = allowSelfVote,
        mafiaCanTargetMafia = mafiaCanTargetMafia,
        voteTieBehavior = voteTieBehavior,
        maxRevotes = maxRevotes,
        mafiaKillTieBehavior = mafiaKillTieBehavior,
    )

    companion object {
        fun from(settings: MafiaSettings): MafiaSetupDraft = MafiaSetupDraft(
            roleCounts = settings.roleCounts,
            revealRoleOnDeath = settings.revealRoleOnDeath,
            doctorCanSelfHeal = settings.doctorCanSelfHeal,
            doctorCanProtectSamePlayerConsecutively =
                settings.doctorCanProtectSamePlayerConsecutively,
            detectiveCanInspectSelf = settings.detectiveCanInspectSelf,
            allowSelfVote = settings.allowSelfVote,
            mafiaCanTargetMafia = settings.mafiaCanTargetMafia,
            voteTieBehavior = settings.voteTieBehavior,
            maxRevotes = settings.maxRevotes,
            mafiaKillTieBehavior = settings.mafiaKillTieBehavior,
        )
    }
}

/** Explicit lists make adding a serialized enum value a setup-contract decision. */
internal val mafiaSetupVoteTieOptions: List<TieBehavior> = listOf(
    TieBehavior.REVOTE_TIED_ONLY,
    TieBehavior.REVOTE_ALL,
    TieBehavior.SKIP_ELIMINATION,
)

internal val mafiaSetupKillTieOptions: List<MafiaKillTie> = listOf(
    MafiaKillTie.REVOTE,
    MafiaKillTie.RANDOM_TIED,
    MafiaKillTie.NO_KILL,
)
