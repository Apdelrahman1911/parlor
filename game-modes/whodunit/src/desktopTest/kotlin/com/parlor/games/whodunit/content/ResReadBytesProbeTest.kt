package com.parlor.games.whodunit.content

import com.parlor.games.whodunit.resources.Res
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test

/**
 * Diagnostic: directly call Res.readBytes and let the failure bubble up so we
 * see the real error (path? wrong resource scope? something else).
 */
@OptIn(ExperimentalResourceApi::class)
class ResReadBytesProbeTest {

    @Test
    fun reads_last_dinner_json() = runTest {
        val bytes = Res.readBytes("files/cases/last-dinner.json")
        check(bytes.isNotEmpty()) { "Read empty bytes from last-dinner.json" }
        val text = bytes.decodeToString()
        check(text.contains("Maxwell")) { "Decoded content does not look like the case JSON" }
    }
}
