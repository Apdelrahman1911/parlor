package com.parlor.games.lastlight.ui.feedback

import com.parlor.games.lastlight.resources.Res
import java.security.MessageDigest
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
class LastLightAudioAssetsTest {
    @Test
    fun everyCueLoadsFromBundledResourcesAndMatchesTheOriginalPcmSample() = runTest {
        val provenance = Json.parseToJsonElement(Res.readBytes("files/audio/provenance.json").decodeToString()).jsonObject
        assertEquals("PartyDeck", provenance.getValue("source_project").jsonPrimitive.content)
        val entries = provenance.getValue("assets").jsonArray.associate { entry ->
            val asset = entry.jsonObject
            asset.getValue("resource_path").jsonPrimitive.content to asset.getValue("sha256").jsonPrimitive.content
        }
        assertEquals(expectedHashes.keys, LastLightFeedbackCue.entries.map { it.fileName }.toSet())
        assertEquals(LastLightFeedbackCue.entries.size, entries.size)

        var totalBytes = 0
        for (cue in LastLightFeedbackCue.entries) {
            val bytes = Res.readBytes(cue.resourcePath)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(expectedHashes.getValue(cue.fileName), hash, cue.fileName)
            assertEquals(hash, entries.getValue(cue.resourcePath))
            totalBytes += bytes.size

            AudioSystem.getAudioInputStream(bytes.inputStream()).use { audio ->
                assertEquals(AudioFormat.Encoding.PCM_SIGNED, audio.format.encoding)
                assertEquals(1, audio.format.channels)
                assertEquals(16, audio.format.sampleSizeInBits)
                assertEquals(44_100f, audio.format.sampleRate)
                assertFalse(audio.format.isBigEndian)
                assertEquals(cue.durationMillis * 44_100L / 1_000L, audio.frameLength, cue.fileName)
                assertTrue(audio.frameLength * audio.format.frameSize < 1_000_000L, "SoundPool's decoded sample bound")
            }
        }
        assertEquals(285_150, totalBytes)
    }

    private companion object {
        val expectedHashes = mapOf(
            "ui_tap.wav" to "ede5e5301c8ebb8d8ee8773dcc64be5e27d0ee2333f6ea46391accd18d357477",
            "card_place.wav" to "fde103b8958a4a167bf733193bc345b98828e46744bfd8b7fbed852510f4739d",
            "challenge.wav" to "939bffe9be3c1060efba0575b66fd1d191a83799d7f33d757c0200355229fc04",
            "safe.wav" to "89f360a56dff9f32585846f7869948a475d62b53be14fc4c8b095da41e4c8e7c",
            "light_out.wav" to "a31c499aeef5aa959cd35fe88b8e422b8d50a6eaf61b9c7629989d094e9e600a",
            "victory.wav" to "7b188e1276e88c8ba8247b438b89444bc2c5f70e9af891656d327758c6616c07",
        )
    }
}
