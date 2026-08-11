package com.parlor.games.whodunit.content

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.content.datasource.InMemoryCachedCaseDataSource
import com.parlor.content.datasource.KtorRemoteCaseDataSource
import com.parlor.content.datasource.RemoteCaseDataSource
import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.CaseSummary
import com.parlor.content.repository.DefaultCaseRepository
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.content.validation.CaseSummaryValidator
import com.parlor.content.schema.IntRangePair
import com.parlor.core.result.Result
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.NetworkError
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
import java.io.File
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
 * The production catalog is the source of truth and **changes break this test
 * on purpose**. A filesystem assertion below also proves that the catalog and
 * packaged JSON resources are exactly the same set.
 */
@OptIn(ExperimentalResourceApi::class)
class CasePickerDiscoveryTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private fun buildRepository(remote: RemoteCaseDataSource? = null): DefaultCaseRepository {
        val bundled = BundledWhodunitCases(
            knownCaseIds = bundledWhodunitCaseIds,
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
            remote = remote ?: KtorRemoteCaseDataSource(emptyRemote, baseUrl = "https://test.local"),
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
        assertThat(surfacedIds).containsAtLeast(*bundledWhodunitCaseIds.toTypedArray())
        // Remote/cache behavior must never add an item outside the production
        // catalog. Resource-to-catalog parity is asserted separately below.
        assertThat(surfacedIds.toSet()).isEqualTo(bundledWhodunitCaseIds.toSet())
    }

    @Test
    fun every_known_case_has_a_valid_envelope() = runTest {
        val repo = buildRepository()
        val result = repo.listCases(WhodunitIds.GameId)
        val summaries = (result as Result.Success).data
        assertThat(summaries).hasSize(bundledWhodunitCaseIds.size)
        for (summary in summaries) {
            assertThat(summary.gameId).isEqualTo(WhodunitIds.GameId.raw)
        }
    }

    @Test
    fun registry_aware_summary_validation_rejects_impossible_mode_and_player_range() {
        val validator = CaseSummaryValidator(
            gameRegistry = DefaultGameRegistry(listOf(WhodunitDefinition(json))),
            installedAppVersion = SemVer(1, 0, 0),
        )
        val valid = com.parlor.content.schema.CaseSummary(
            caseId = "valid",
            title = "Valid",
            version = SemVer(1, 0, 0),
            gameId = WhodunitIds.GameId.raw,
            supportedPlayerCounts = IntRangePair(4, 6),
            supportedModes = listOf("classic-vote"),
            language = "en",
            theme = "test",
            estimatedDuration = IntRangePair(10, 20),
            minimumAppVersion = SemVer(1, 0, 0),
        )

        assertThat(
            validator.validate(
                WhodunitIds.GameId,
                listOf(valid.copy(supportedPlayerCounts = IntRangePair(3, 9))),
            ),
        ).isInstanceOf(Result.Failure::class)
        assertThat(
            validator.validate(
                WhodunitIds.GameId,
                listOf(valid.copy(supportedModes = listOf("not-a-mode"))),
            ),
        ).isInstanceOf(Result.Failure::class)
    }

    @Test
    fun repository_discards_a_corrupt_remote_manifest_and_uses_bundled_cases() = runTest {
        val maliciousRemote = object : RemoteCaseDataSource {
            override suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, NetworkError> =
                Result.Success(
                    listOf(
                        CaseSummary(
                            caseId = "../escape",
                            title = "Injected",
                            version = SemVer(1, 0, 0),
                            gameId = gameId.raw,
                            supportedPlayerCounts = IntRangePair(4, 6),
                            supportedModes = listOf("classic-vote"),
                            language = "en",
                            theme = "test",
                            estimatedDuration = IntRangePair(1, 2),
                            minimumAppVersion = SemVer(1, 0, 0),
                        ),
                    ),
                )

            override suspend fun fetchCase(id: CaseId): Result<CaseEnvelope, NetworkError> =
                Result.Failure(NetworkError.Unreachable)
        }

        val result = buildRepository(maliciousRemote).listCases(WhodunitIds.GameId)
        val summaries = (result as Result.Success).data
        assertThat(summaries.map { it.caseId }.toSet()).isEqualTo(bundledWhodunitCaseIds.toSet())
    }

    @Test
    fun production_catalog_exactly_matches_packaged_case_resources() {
        val caseDirectory = File(
            findProjectRoot(),
            "game-modes/whodunit/src/commonMain/composeResources/files/cases",
        )
        val resourceFiles = caseDirectory.listFiles()
            .orEmpty()
            .filter { file -> file.isFile }
            .map { file -> file.name }
            .toSet()
        val expectedFiles = bundledWhodunitCaseIds.map { caseId -> "$caseId.json" }.toSet()

        assertThat(resourceFiles).isEqualTo(expectedFiles)
        assertThat(resourceFiles).hasSize(bundledWhodunitCaseIds.size)
    }

    private fun findProjectRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate project root")
    }
}
