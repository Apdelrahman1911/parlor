package com.parlor.app.di

import com.parlor.content.datasource.BundledFallbackCaseDataSource
import com.parlor.content.datasource.CachedCaseDataSource
import com.parlor.content.datasource.InMemoryCachedCaseDataSource
import com.parlor.content.datasource.KtorRemoteCaseDataSource
import com.parlor.content.datasource.RemoteCaseDataSource
import com.parlor.content.repository.CaseRepository
import com.parlor.content.repository.DefaultCaseRepository
import com.parlor.content.validation.CaseValidator
import com.parlor.content.validation.DefaultCaseValidator
import com.parlor.core.ids.CaseId
import com.parlor.core.versioning.SemVer
import com.parlor.engine.registry.DefaultGameRegistry
import com.parlor.engine.registry.GameRegistry
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.whodunit.WhodunitDefinition
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Content delivery pipeline.
 *
 * Wires:
 *  - `GameRegistry` — the installed games. For MVP this is just Whodunit.
 *  - `HttpClient` with `MockEngine` — the in-process "backend" used for dev.
 *    Production swaps this for a real engine pointed at the production
 *    backend URL.
 *  - `RemoteCaseDataSource` — `KtorRemoteCaseDataSource` against the mock.
 *  - `CachedCaseDataSource` — in-memory (Phase 6 swaps in a persistent backing).
 *  - `BundledFallbackCaseDataSource` — comes from the Whodunit module's DI
 *    (already bound there; consumed un-qualified here).
 *  - `CaseValidator` — `DefaultCaseValidator` with strict envelope rules.
 *  - `CaseRepository` — `DefaultCaseRepository` with the Remote→Cache→Bundled
 *    fallback chain.
 *
 * The mock backend serves the same JSON the bundled fallback would return —
 * one source of truth, no per-platform duplication. Per
 * `docs/MOCK_BACKEND.md`, the production code path (HttpClient → repository →
 * validator) is the only content path; the mock just sits behind it.
 */
val contentModule: Module = module {

    single<GameRegistry> {
        DefaultGameRegistry(
            listOf(
                get<WhodunitDefinition>(),
                get<MafiaDefinition>(),
            ),
        )
    }

    single<HttpClient> {
        buildMockHttpClient(
            json = get(),
            bundled = get(),
        )
    }

    single<RemoteCaseDataSource> {
        KtorRemoteCaseDataSource(
            client = get(),
            baseUrl = MOCK_BASE_URL,
        )
    }

    single<CachedCaseDataSource> { InMemoryCachedCaseDataSource() }

    single<CaseValidator> {
        DefaultCaseValidator(
            json = get(),
            knownSchemaVersion = SUPPORTED_SCHEMA_VERSION,
            installedAppVersion = INSTALLED_APP_VERSION,
            gameRegistry = get(),
        )
    }

    single<CaseRepository> {
        DefaultCaseRepository(
            remote = get(),
            cache = get(),
            bundled = get(),
            validator = get(),
            json = get(),
        )
    }
}

/** Phase 8 reads this from BuildConfig; constant for now. */
private val INSTALLED_APP_VERSION: SemVer = SemVer(1, 0, 0)

/** The schema this app build understands. Bumping this requires app code changes. */
private const val SUPPORTED_SCHEMA_VERSION: Int = 1

/** Synthetic base URL — never reaches the network in dev (MockEngine intercepts). */
private const val MOCK_BASE_URL: String = "https://parlor-mock.local"

/**
 * Builds the dev `HttpClient` with `MockEngine` handlers that serve the same
 * JSON the bundled fallback would. The mock answers two routes:
 *
 *  - `GET /games/{gameId}/cases` → manifest (List<CaseSummary>)
 *  - `GET /cases/{caseId}`       → CaseEnvelope JSON
 *
 * Unknown routes return 404. Unknown case ids return 404. Both responses use
 * `application/json` Content-Type so ContentNegotiation parses correctly.
 *
 * The mock reads case content via [BundledFallbackCaseDataSource], so adding
 * a new case to the dev backend = adding a new JSON file under
 * `composeResources/files/cases/`. No backend service to maintain.
 */
private fun buildMockHttpClient(
    json: Json,
    bundled: BundledFallbackCaseDataSource,
): HttpClient {
    val engine = MockEngine { request ->
        val path = request.url.encodedPath
        when {
            // /games/{gameId}/cases — manifest
            path.matches(MANIFEST_PATH) -> {
                val summaries = bundled.availableCases().filter { summary ->
                    val gameId = MANIFEST_PATH.find(path)?.groupValues?.get(1)
                    gameId == null || summary.gameId == gameId
                }
                val body = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(
                        com.parlor.content.schema.CaseSummary.serializer(),
                    ),
                    summaries,
                )
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

            // /cases/{caseId} — envelope
            path.matches(CASE_PATH) -> {
                val caseId = CASE_PATH.find(path)?.groupValues?.get(1)
                val envelope = caseId?.let { bundled.loadBundled(CaseId(it)) }
                if (envelope == null) {
                    respond(
                        content = ByteReadChannel.Empty,
                        status = HttpStatusCode.NotFound,
                    )
                } else {
                    val body = json.encodeToString(
                        com.parlor.content.schema.CaseEnvelope.serializer(),
                        envelope,
                    )
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }

            else -> respond(
                content = ByteReadChannel.Empty,
                status = HttpStatusCode.NotFound,
            )
        }
    }
    return HttpClient(engine) {
        install(ContentNegotiation) {
            json(json)
        }
    }
}

private val MANIFEST_PATH = Regex("^/games/([^/]+)/cases$")
private val CASE_PATH = Regex("^/cases/([^/]+)$")
