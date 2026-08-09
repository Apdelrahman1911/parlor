package com.parlor.content.datasource

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.NetworkError
import com.parlor.core.result.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfflineRemoteCaseDataSourceTest {

    @Test
    fun bundled_only_policy_reports_remote_as_unreachable() = runTest {
        val source = OfflineRemoteCaseDataSource()

        assertEquals(
            Result.Failure(NetworkError.Unreachable),
            source.listCases(GameId("whodunit")),
        )
        assertEquals(
            Result.Failure(NetworkError.Unreachable),
            source.fetchCase(CaseId("last-dinner")),
        )
    }

    @Test
    fun ktor_source_maps_non_success_status_before_deserializing_error_body() = runTest {
        suspend fun fetch(status: HttpStatusCode): Result<*, NetworkError> {
            val client = HttpClient(MockEngine { respond("not-json", status = status) })
            return try {
                KtorRemoteCaseDataSource(client, "https://content.test")
                    .fetchCase(CaseId("missing"))
            } finally {
                client.close()
            }
        }

        assertEquals(
            Result.Failure(NetworkError.Unauthorized),
            fetch(HttpStatusCode.Unauthorized),
        )
        assertEquals(
            Result.Failure(NetworkError.Server(503)),
            fetch(HttpStatusCode.ServiceUnavailable),
        )
    }

    @Test
    fun ktor_source_propagates_structured_cancellation() = runTest {
        val client = HttpClient(
            MockEngine {
                throw CancellationException("request cancelled")
            },
        )
        val source = KtorRemoteCaseDataSource(client, "https://content.test")

        try {
            assertFailsWith<CancellationException> {
                source.fetchCase(CaseId("cancelled"))
            }
            assertFailsWith<CancellationException> {
                source.listCases(GameId("whodunit"))
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun ktor_source_rejects_oversized_chunked_bodies_before_json_decode() = runTest {
        val client = HttpClient(
            MockEngine {
                respond(ByteReadChannel(ByteArray(MAX_CASE_RESPONSE_BYTES + 1) { 'x'.code.toByte() }))
            },
        )
        val source = KtorRemoteCaseDataSource(client, "https://content.test")

        try {
            assertEquals(
                Result.Failure(NetworkError.Serialization("response exceeds byte limit")),
                source.fetchCase(CaseId("oversized")),
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun ktor_source_does_not_convert_fatal_errors_into_network_failures() = runTest {
        val client = HttpClient(MockEngine { throw AssertionError("fatal adapter bug") })
        val source = KtorRemoteCaseDataSource(client, "https://content.test")

        try {
            assertFailsWith<AssertionError> {
                source.fetchCase(CaseId("fatal"))
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun ktor_source_rejects_path_unsafe_ids_before_request() = runTest {
        val client = HttpClient(MockEngine { error("request must not be made") })
        val source = KtorRemoteCaseDataSource(client, "https://content.test")
        try {
            assertEquals(
                Result.Failure(NetworkError.Serialization("invalid case id")),
                source.fetchCase(CaseId("../secrets")),
            )
            assertEquals(
                Result.Failure(NetworkError.Serialization("invalid game id")),
                source.listCases(GameId("../whodunit")),
            )
        } finally {
            client.close()
        }
    }
}
