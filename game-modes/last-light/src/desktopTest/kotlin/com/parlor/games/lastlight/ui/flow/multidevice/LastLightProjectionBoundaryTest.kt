package com.parlor.games.lastlight.ui.flow.multidevice

import com.parlor.games.lastlight.domain.projection.LastLightProjectionPolicy
import com.parlor.games.lastlight.domain.state.LastLightHostOnly
import com.parlor.games.lastlight.domain.state.LastLightPrivate
import com.parlor.games.lastlight.domain.state.LastLightState
import com.parlor.games.lastlight.protocol.LastLightProjectionCodec
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LastLightProjectionBoundaryTest {
    @Test
    fun actual_host_snapshots_and_peer_controllers_expose_only_each_recipients_hand() = runTest {
        val fixture = LastLightBridgeFixture(this, requireStartHandshake = true)
        fixture.attachPeers(handshake = true)
        val canonical = fixture.session.currentState()
        fixture.peers.forEach { (id, peer) ->
            val snapshot = fixture.latestSnapshot(id)
            val public = LastLightProjectionCodec.decodePublic(snapshot.publicPayload)
            val own = LastLightProjectionCodec.decodePrivate(snapshot.privatePayload)
            assertEquals(LastLightProjectionPolicy.toPublic(canonical).state, public)
            assertEquals(canonical.privatePerPlayer.getValue(id), own)
            assertEquals(LastLightHostOnly.Redacted, public.hostOnly)
            assertTrue(public.privatePerPlayer.isEmpty())
            val publicText = snapshot.publicPayload.decodeToString()
            val privateText = snapshot.privatePayload.decodeToString()
            listOf("randomSeed", "burnoutSteps", "pendingCards", "undealtCards", "discardedCards", "history").forEach {
                assertFalse(publicText.contains(it))
                assertFalse(privateText.contains(it))
            }
            canonical.privatePerPlayer.forEach { (owner, hand) ->
                hand.hand.forEach { card ->
                    assertFalse(publicText.contains("\"${card.id}\""))
                    if (owner != id) assertFalse(privateText.contains("\"${card.id}\""))
                }
            }
            val installed = peer.controller.privateStateFor(id).value.state
            assertEquals(setOf(id), installed.privatePerPlayer.keys)
            assertEquals(LastLightHostOnly.Redacted, installed.hostOnly)
            assertEquals(own.hand, LastLightProjectionPolicy.viewFor(installed, id).yourHand)
            assertNull(peer.controller.hostState)
            assertNull(peer.controller.canonicalState)
            assertFailsWith<IllegalArgumentException> { peer.controller.privateStateFor(testHostId) }
        }
        val hostSeat = fixture.session.privateStateFor(testHostId).value.state
        assertEquals(setOf(testHostId), hostSeat.privatePerPlayer.keys)
        assertEquals(LastLightHostOnly.Redacted, hostSeat.hostOnly)
        fixture.close()
    }

    @Test
    fun canonical_host_payload_is_rejected_before_any_peer_projection_changes() = runTest {
        val fixture = LastLightBridgeFixture(this)
        fixture.attachPeers()
        val peer = fixture.peers.getValue(testAliceId)
        val before = peer.controller.privateStateFor(testAliceId).value
        val original = fixture.latestSnapshot(testAliceId)
        val canonicalPayload = Json.encodeToString(LastLightState.serializer(), fixture.session.currentState()).encodeToByteArray()
        fixture.deliver(
            SendTarget.Direct(testAliceId),
            original.copy(
                header = original.header.copy(messageId = fixture.header("host-secret").messageId, sequence = original.header.sequence + 1_000),
                revision = original.revision + 1,
                publicPayload = canonicalPayload,
            ),
        )
        runCurrent()
        assertEquals(before, peer.controller.privateStateFor(testAliceId).value)
        assertEquals(SessionEndReason.IncompatibleVersion, peer.terminalReason.value)
        assertTrue(peer.controller.publicState.value.state.privatePerPlayer.isEmpty())
        fixture.close()
    }

    @Test
    fun missing_or_inconsistent_private_hand_cannot_partially_install_new_public_state() = runTest {
        listOf(ByteArray(0), LastLightProjectionCodec.encodePrivate(LastLightPrivate())).forEach { privatePayload ->
            val fixture = LastLightBridgeFixture(this)
            fixture.attachPeers()
            val peer = fixture.peers.getValue(testAliceId)
            val beforePublic = peer.controller.publicState.value
            val beforePrivate = peer.controller.privateStateFor(testAliceId).value
            val original = fixture.latestSnapshot(testAliceId)
            fixture.deliver(
                SendTarget.Direct(testAliceId),
                original.copy(
                    header = original.header.copy(messageId = fixture.header("missing-hand").messageId, sequence = original.header.sequence + 1_000),
                    revision = original.revision + 1,
                    privatePayload = privatePayload,
                ),
            )
            runCurrent()
            assertEquals(beforePublic, peer.controller.publicState.value)
            assertEquals(beforePrivate, peer.controller.privateStateFor(testAliceId).value)
            assertEquals(SessionEndReason.IncompatibleVersion, peer.terminalReason.value)
            fixture.close()
        }
    }

    @Test
    fun otherwise_valid_snapshot_cannot_replace_the_admitted_roster() = runTest {
        val fixture = LastLightBridgeFixture(this)
        fixture.attachPeers()
        val peer = fixture.peers.getValue(testAliceId)
        val before = peer.controller.privateStateFor(testAliceId).value
        val public = LastLightProjectionPolicy.toPublic(fixture.session.currentState()).state
        val changed = public.copy(
            players = public.players.map { if (it.id == testHostId) it.copy(displayName = "Different host") else it },
            public = public.public.copy(roster = public.public.roster.map {
                if (it.id == testHostId.raw) it.copy(displayName = "Different host") else it
            }),
        )
        val original = fixture.latestSnapshot(testAliceId)
        fixture.deliver(
            SendTarget.Direct(testAliceId),
            original.copy(
                header = original.header.copy(messageId = fixture.header("roster").messageId, sequence = original.header.sequence + 1_000),
                revision = original.revision + 1,
                publicPayload = LastLightProjectionCodec.encodePublic(changed),
            ),
        )
        runCurrent()
        assertEquals(before, peer.controller.privateStateFor(testAliceId).value)
        assertEquals(SessionEndReason.IncompatibleVersion, peer.terminalReason.value)
        fixture.close()
    }

    @Test
    fun delayed_and_duplicate_snapshots_never_restore_played_cards() = runTest {
        val seed = seedWithState { it.public.turnPlayerId == testAliceId.raw }
        val fixture = LastLightBridgeFixture(this, seed = seed)
        fixture.attachPeers()
        val old = fixture.latestSnapshot(testAliceId)
        fixture.playCurrent()
        val peer = fixture.peers.getValue(testAliceId)
        val after = peer.controller.privateStateFor(testAliceId).value
        assertEquals(4, after.state.privatePerPlayer.getValue(testAliceId).hand.size)
        fixture.deliver(SendTarget.Direct(testAliceId), old)
        fixture.deliver(SendTarget.Direct(testAliceId), fixture.latestSnapshot(testAliceId))
        runCurrent()
        assertEquals(after, peer.controller.privateStateFor(testAliceId).value)
        assertNull(peer.terminalReason.value)
        fixture.close()
    }
}
