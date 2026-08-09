package com.parlor.games.whodunit.content

import com.parlor.content.schema.CaseEnvelope
import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.versioning.SemVer
import com.parlor.engine.state.Player
import com.parlor.games.whodunit.resources.Res
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.SessionEnvelopeHeader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalResourceApi::class)
class WhodunitContentIdentityTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun canonical_digest_ignores_object_key_order_and_delivery_signature() = runTest {
        val original = loadEnvelope()
        val reorderedPayload = JsonObject(
            original.payload.jsonObject.entries.reversed()
                .associate { (key, value) -> key to value },
        )
        val equivalent = original.copy(
            payload = reorderedPayload,
            signature = "different-delivery-signature",
        )

        assertEquals(original.contentIdentity(), equivalent.contentIdentity())
    }

    @Test
    fun same_case_id_with_different_version_or_payload_is_rejected() = runTest {
        val original = loadEnvelope()
        val identity = original.contentIdentity()
        val offer = offer(identity)

        assertTrue(offer.matches(original))
        assertFalse(offer.matches(original.copy(version = SemVer(1, 0, 1))))

        val changedPayload = JsonObject(
            original.payload.jsonObject + ("publicIntro" to JsonPrimitive("Changed intro")),
        )
        assertFalse(offer.matches(original.copy(payload = changedPayload)))
    }

    private suspend fun loadEnvelope(): CaseEnvelope = json.decodeFromString(
        CaseEnvelope.serializer(),
        Res.readBytes("files/cases/last-dinner.json").decodeToString(),
    )

    private fun offer(identity: WhodunitContentIdentity) = HostMessage.SessionStarting(
        startId = "start-012345678901234567890",
        caseId = "last-dinner",
        modeId = "classic-vote",
        players = listOf(
            Player(PlayerId("p1"), "One", 0),
            Player(PlayerId("p2"), "Two", 1),
        ),
        sessionNonce = 1L,
        header = SessionEnvelopeHeader(
            protocol = ProtocolVersion(),
            sessionId = SessionId("session-0123456789"),
            gameId = GameId("whodunit"),
            gameVersion = 1,
            messageId = "start-012345678901234567890",
            sequence = 0L,
        ),
        caseVersion = identity.version,
        caseDigest = identity.digest,
    )
}
