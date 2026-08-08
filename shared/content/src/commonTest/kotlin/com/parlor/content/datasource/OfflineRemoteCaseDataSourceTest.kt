package com.parlor.content.datasource

import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.NetworkError
import com.parlor.core.result.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
