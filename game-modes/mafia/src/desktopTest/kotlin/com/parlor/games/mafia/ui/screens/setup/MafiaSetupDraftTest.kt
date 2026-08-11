package com.parlor.games.mafia.ui.screens.setup

import com.parlor.games.mafia.domain.settings.MafiaKillTie
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.settings.TieBehavior
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.ExperimentalSerializationApi

class MafiaSetupDraftTest {
    @Test
    fun round_trip_preserves_every_shipping_editable_rule() {
        val settings = MafiaSettings(
            roleCounts = MafiaRoleCounts(mafia = 2, detective = 0, doctor = 1),
            revealRoleOnDeath = false,
            doctorCanSelfHeal = true,
            doctorCanProtectSamePlayerConsecutively = true,
            detectiveCanInspectSelf = true,
            allowSelfVote = true,
            mafiaCanTargetMafia = true,
            voteTieBehavior = TieBehavior.REVOTE_ALL,
            maxRevotes = 3,
            mafiaKillTieBehavior = MafiaKillTie.NO_KILL,
        )

        assertEquals(settings, MafiaSetupDraft.from(settings).applyTo(settings))
    }

    @Test
    fun edits_every_shipping_rule_without_changing_compatibility_only_timers() {
        val base = MafiaSettings(
            roleCounts = MafiaRoleCounts(mafia = 1, detective = 1, doctor = 0),
            nightDurationSeconds = 30,
            discussionDurationSeconds = 45,
            voteDurationSeconds = 20,
        )
        val draft = MafiaSetupDraft(
            roleCounts = MafiaRoleCounts(mafia = 2, detective = 0, doctor = 1),
            revealRoleOnDeath = false,
            doctorCanSelfHeal = true,
            doctorCanProtectSamePlayerConsecutively = true,
            detectiveCanInspectSelf = true,
            allowSelfVote = true,
            mafiaCanTargetMafia = true,
            voteTieBehavior = TieBehavior.SKIP_ELIMINATION,
            maxRevotes = MafiaSettings.MAX_REVOTES,
            mafiaKillTieBehavior = MafiaKillTie.RANDOM_TIED,
        )

        assertEquals(
            base.copy(
                roleCounts = draft.roleCounts,
                revealRoleOnDeath = draft.revealRoleOnDeath,
                doctorCanSelfHeal = draft.doctorCanSelfHeal,
                doctorCanProtectSamePlayerConsecutively =
                    draft.doctorCanProtectSamePlayerConsecutively,
                detectiveCanInspectSelf = draft.detectiveCanInspectSelf,
                allowSelfVote = draft.allowSelfVote,
                mafiaCanTargetMafia = draft.mafiaCanTargetMafia,
                voteTieBehavior = draft.voteTieBehavior,
                maxRevotes = draft.maxRevotes,
                mafiaKillTieBehavior = draft.mafiaKillTieBehavior,
            ),
            draft.applyTo(base),
        )
    }

    @Test
    fun setup_options_are_exhaustive_for_serialized_rule_enums() {
        assertEquals(TieBehavior.entries.toSet(), mafiaSetupVoteTieOptions.toSet())
        assertEquals(MafiaKillTie.entries.toSet(), mafiaSetupKillTieOptions.toSet())
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun setup_contract_classifies_every_serialized_setting() {
        val descriptor = MafiaSettings.serializer().descriptor
        val serializedSettings = (0 until descriptor.elementsCount)
            .map(descriptor::getElementName)
            .toSet()

        assertEquals(
            setOf(
                "roleCounts",
                "revealRoleOnDeath",
                "doctorCanSelfHeal",
                "doctorCanProtectSamePlayerConsecutively",
                "detectiveCanInspectSelf",
                "allowSelfVote",
                "mafiaCanTargetMafia",
                "voteTieBehavior",
                "maxRevotes",
                "mafiaKillTieBehavior",
                // Retained only for backward decoding. Validation rejects
                // non-null values until timed phase transitions exist.
                "nightDurationSeconds",
                "discussionDurationSeconds",
                "voteDurationSeconds",
            ),
            serializedSettings,
        )
    }
}
