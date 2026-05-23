package com.parlor.games.whodunit.content

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.parlor.content.repository.DefaultCaseRepository
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.core.ids.CaseId
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.games.whodunit.WhodunitDefinition
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
 * Validates the bundled Arabic cases (`layla-halabi`, `jasmine-ring`) through
 * the same envelope + payload validators as production, so a structural
 * problem in either Arabic JSON file surfaces at build time rather than at
 * the table.
 *
 * The cases are intended for play-test with Arabic-speaking groups; the
 * validator does not care about language, but rule 11 (kebab-case ids) and
 * rule 15 (reveal narrative keys == character ids) are still strict.
 */
@OptIn(ExperimentalResourceApi::class)
class ArabicCaseValidationTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private fun buildBundled(caseIds: List<String>) = BundledWhodunitCases(
        knownCaseIds = caseIds,
        loadJson = { caseId ->
            runCatching { Res.readBytes("files/cases/$caseId.json").decodeToString() }.getOrNull()
        },
        json = json,
    )

    private fun buildRepository(caseIds: List<String>): DefaultCaseRepository {
        val bundled = buildBundled(caseIds)
        val emptyRemote = HttpClient(MockEngine { _ ->
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NotFound)
        }) {
            install(ContentNegotiation) { json(json) }
        }
        return DefaultCaseRepository(
            remote = com.parlor.content.datasource.KtorRemoteCaseDataSource(
                client = emptyRemote,
                baseUrl = "https://test.local",
            ),
            cache = com.parlor.content.datasource.InMemoryCachedCaseDataSource(),
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
    fun layla_halabi_case_loads_validates_and_carries_arabic_content() = runTest {
        val repo = buildRepository(listOf("layla-halabi"))
        val result = repo.loadCase(CaseId("layla-halabi"), WhodunitPayloadValidator(json))
        assertThat(result).isInstanceOf(Result.Success::class)
        val validated = (result as Result.Success).data
        assertThat(validated.envelope.language).isEqualTo("ar")
        assertThat(validated.envelope.supportedModes).contains("classic-vote")
        assertThat(validated.envelope.supportedModes).contains("elimination")
        assertThat(validated.payload.characters).hasSize(6)
        // Per rule 15, every character id has a reveal narrative entry.
        val ids = validated.payload.characters.map { it.id }.toSet()
        assertThat(validated.payload.revealNarratives.keys).isEqualTo(ids)
    }

    @Test
    fun jasmine_ring_case_loads_validates_and_carries_arabic_content() = runTest {
        val repo = buildRepository(listOf("jasmine-ring"))
        val result = repo.loadCase(CaseId("jasmine-ring"), WhodunitPayloadValidator(json))
        assertThat(result).isInstanceOf(Result.Success::class)
        val validated = (result as Result.Success).data
        assertThat(validated.envelope.language).isEqualTo("ar")
        assertThat(validated.payload.characters).hasSize(6)
        // Spot-check a kebab-case id (rule 11) and the cross-reference to
        // the reveal narratives map (rule 15).
        assertThat(validated.payload.revealNarratives.keys).contains("walid-qasem")
        assertThat(validated.payload.revealNarratives.keys).contains("nadim-shams")
    }

    @Test
    fun both_arabic_cases_share_the_bundled_data_source_without_collisions() = runTest {
        // The whodunit DI module registers all three cases under one
        // BundledFallbackCaseDataSource. This mirrors that wiring exactly so a
        // clue-id collision between the two Arabic cases (or with the
        // English one) would fail here.
        val bundled = buildBundled(listOf("last-dinner", "layla-halabi", "jasmine-ring"))
        val summaries = bundled.availableCases()
        val ids = summaries.map { it.caseId }.toSet()
        assertThat(ids).isEqualTo(setOf("last-dinner", "layla-halabi", "jasmine-ring"))

        // Validate each through the same payload validator to confirm none
        // step on the others. Rule 16 (clue-id global uniqueness) only fires
        // within a single case, but a case-id collision would explode here.
        val validator = WhodunitPayloadValidator(json)
        listOf("layla-halabi", "jasmine-ring").forEach { caseId ->
            val envelope = bundled.loadBundled(CaseId(caseId))!!
            val result = validator.validate(envelope)
            assertThat(result, "validating $caseId").isInstanceOf(Result.Success::class)
        }
    }
}
