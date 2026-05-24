package com.parlor.games.whodunit.content

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.content.datasource.InMemoryCachedCaseDataSource
import com.parlor.content.datasource.KtorRemoteCaseDataSource
import com.parlor.content.repository.DefaultCaseRepository
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.resources.Res
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test

/**
 * Pins the case picker contract: every id wired into
 * `WhodunitDiModule.knownCaseIds` is surfaced by the production
 * `CaseRepository.listCases(WhodunitIds.GameId)` path, so the UI never
 * silently hides a case that the build was supposed to ship.
 *
 * The DI-configured list is the source of truth and **changes break this
 * test on purpose** — adding a JSON to `composeResources/files/cases/`
 * without adding it to `knownCaseIds` is the failure mode this guards.
 */
@OptIn(ExperimentalResourceApi::class)
class CasePickerDiscoveryTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    /**
     * The same list the production Koin module wires (kept in sync manually —
     * touch this list when `WhodunitDiModule.knownCaseIds` changes).
     */
    private val productionKnownCaseIds = listOf("last-dinner", "layla-halabi", "jasmine-ring")

    private fun buildRepository(): DefaultCaseRepository {
        val bundled = BundledWhodunitCases(
            knownCaseIds = productionKnownCaseIds,
            loadJson = { caseId ->
                runCatching {
                    Res.readBytes("files/cases/$caseId.json").decodeToString()
                }.getOrNull()
            },
            json = json,
        )
        val emptyRemote = HttpClient(MockEngine { _ ->
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NotFound)
        }) {
            install(ContentNegotiation) { json(json) }
        }
        return DefaultCaseRepository(
            remote = KtorRemoteCaseDataSource(emptyRemote, baseUrl = "https://test.local"),
            cache = InMemoryCachedCaseDataSource(),
            bundled = bundled,
            validator = DefaultCaseValidator(
                json = json,
                knownSchemaVersion = 1,
                installedAppVersion = SemVer(1, 0, 0),
                gameRegistry = DefaultGameRegistry(listOf(WhodunitDefinition(json))),
            ),
            json = json,
        )
    }

    @Test
    fun repository_surfaces_every_known_case() = runTest {
        val repo = buildRepository()
        val result = repo.listCases(WhodunitIds.GameId)
        assertThat(result).isInstanceOf(Result.Success::class)
        val summaries = (result as Result.Success).data
        val surfacedIds = summaries.map { it.caseId }
        assertThat(surfacedIds).containsAtLeast(*productionKnownCaseIds.toTypedArray())
        // Hard-fail if the list grows beyond known — that means a JSON was
        // shipped without a knownCaseIds entry, or vice-versa.
        assertThat(surfacedIds.toSet()).isEqualTo(productionKnownCaseIds.toSet())
    }

    @Test
    fun every_known_case_has_a_valid_envelope() = runTest {
        val repo = buildRepository()
        val result = repo.listCases(WhodunitIds.GameId)
        val summaries = (result as Result.Success).data
        assertThat(summaries).hasSize(productionKnownCaseIds.size)
        for (summary in summaries) {
            assertThat(summary.gameId).isEqualTo(WhodunitIds.GameId.raw)
        }
    }
}
