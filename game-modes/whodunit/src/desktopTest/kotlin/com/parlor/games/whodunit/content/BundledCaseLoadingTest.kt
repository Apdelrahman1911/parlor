package com.parlor.games.whodunit.content

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.parlor.content.repository.DefaultCaseRepository
import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test

/**
 * End-to-end test: the bundled `last-dinner.json` (one source of truth, shipped
 * inside `:game-modes:whodunit/src/commonMain/composeResources/files/cases/`)
 * is loaded via `Res.readBytes`, parsed by [BundledWhodunitCases], wrapped by a
 * [DefaultCaseRepository], and validated by [DefaultCaseValidator] +
 * [WhodunitPayloadValidator]. The end result is a `ValidatedCase` that the
 * UI / session controller can consume.
 *
 * This is the Phase 3 acceptance check from `docs/APP_PLAN.md` §5: *"App
 * fetches, validates, caches, and loads The Last Dinner through the production
 * code path."* Run on Desktop (JVM); resource access is identical on Android
 * and iOS.
 */
@OptIn(ExperimentalResourceApi::class)
class BundledCaseLoadingTest {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    private fun buildBundled() = BundledWhodunitCases(
        knownCaseIds = listOf("last-dinner"),
        loadJson = { caseId ->
            runCatching {
                Res.readBytes("files/cases/$caseId.json").decodeToString()
            }.getOrNull()
        },
        json = json,
    )

    private fun buildValidator() = DefaultCaseValidator(
        json = json,
        knownSchemaVersion = 1,
        installedAppVersion = SemVer(1, 0, 0),
        gameRegistry = DefaultGameRegistry(listOf(WhodunitDefinition(json))),
    )

    @Test
    fun bundled_resource_loads_last_dinner_envelope() = runTest {
        val bundled = buildBundled()

        val summaries = bundled.availableCases()
        assertThat(summaries).hasSize(1)
        val summary = summaries.first()
        assertThat(summary.caseId).isEqualTo("last-dinner")
        assertThat(summary.title).isEqualTo("The Last Dinner")
        assertThat(summary.gameId).isEqualTo(WhodunitIds.GameId.raw)

        val envelope = requireNotNull(bundled.loadBundled(CaseId("last-dinner"))) {
            "last-dinner must be present in the bundled catalog"
        }
        assertThat(envelope.caseId).isEqualTo("last-dinner")
        assertThat(envelope.supportedModes).containsExactly("classic-vote", "elimination")
    }

    @Test
    fun unknown_case_id_returns_null() = runTest {
        val envelope = buildBundled().loadBundled(CaseId("not-a-real-case"))
        assertThat(envelope).isEqualTo(null)
    }

    @Test
    fun repository_resolves_last_dinner_through_bundled_when_remote_returns_empty() = runTest {
        val bundled = buildBundled()
        val emptyRemote = HttpClient(MockEngine { _ ->
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NotFound)
        }) {
            install(ContentNegotiation) { json(json) }
        }
        val repository = DefaultCaseRepository(
            remote = com.parlor.content.datasource.KtorRemoteCaseDataSource(
                client = emptyRemote,
                baseUrl = "https://test.local",
            ),
            cache = com.parlor.content.datasource.InMemoryCachedCaseDataSource(),
            bundled = bundled,
            validator = buildValidator(),
            json = json,
        )

        val result = repository.loadCase(
            id = CaseId("last-dinner"),
            payloadValidator = WhodunitPayloadValidator(json),
        )
        assertThat(result).isInstanceOf(Result.Success::class)
        val validated = (result as Result.Success).data
        assertThat(validated.envelope.caseId).isEqualTo("last-dinner")
        assertThat(validated.payload.characters).hasSize(6)
        assertThat(validated.payload.characters.map { it.id })
            .containsExactly(
                "eleanor-hargrove",
                "daniel-hargrove",
                "vivienne-cross",
                "james-sutton",
                "clara-bell",
                "henry-vance",
            )
    }

    @Test
    fun repository_resolves_last_dinner_through_remote_mock_engine() = runTest {
        val bundled = buildBundled()
        // Mock the production HTTP path: serve the bundled JSON on /cases/{id}.
        val mockClient = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val caseId = Regex("^/cases/([^/]+)$").find(path)?.groupValues?.get(1)
            val envelope = caseId?.let { bundled.loadBundled(CaseId(it)) }
            if (envelope == null) {
                respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NotFound)
            } else {
                val body = json.encodeToString(CaseEnvelope.serializer(), envelope)
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }) {
            install(ContentNegotiation) { json(json) }
        }
        val repository = DefaultCaseRepository(
            remote = com.parlor.content.datasource.KtorRemoteCaseDataSource(
                client = mockClient,
                baseUrl = "https://parlor-mock.local",
            ),
            cache = com.parlor.content.datasource.InMemoryCachedCaseDataSource(),
            bundled = bundled,
            validator = buildValidator(),
            json = json,
        )

        val result = repository.loadCase(
            id = CaseId("last-dinner"),
            payloadValidator = WhodunitPayloadValidator(json),
        )
        assertThat(result is Result.Success).isTrue()
        val validated = (result as Result.Success).data
        assertThat(validated.envelope.caseId).isEqualTo("last-dinner")
        assertThat(validated.envelope.gameId).isEqualTo("whodunit")
    }

    @Test
    fun repository_returns_not_found_when_no_source_has_the_case() = runTest {
        val bundled = buildBundled()
        val emptyRemote = HttpClient(MockEngine { _ ->
            respond(content = ByteReadChannel.Empty, status = HttpStatusCode.NotFound)
        }) {
            install(ContentNegotiation) { json(json) }
        }
        val repository = DefaultCaseRepository(
            remote = com.parlor.content.datasource.KtorRemoteCaseDataSource(
                client = emptyRemote,
                baseUrl = "https://test.local",
            ),
            cache = com.parlor.content.datasource.InMemoryCachedCaseDataSource(),
            bundled = bundled,
            validator = buildValidator(),
            json = json,
        )

        val result = repository.loadCase(
            id = CaseId("ghost-case"),
            payloadValidator = WhodunitPayloadValidator(json),
        )
        assertThat(result is Result.Failure).isTrue()
    }
}
