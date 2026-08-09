package com.parlor.content.datasource

import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.CaseSummary
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.NetworkError
import com.parlor.core.result.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Ktor-backed remote source. Dev builds inject an HttpClient with the
 * MockEngine pointed at static JSON in-repo; production injects a real engine
 * pointed at the case-management backend.
 *
 * Either way, this is the *only* content code path. No inline shortcut exists.
 */
class KtorRemoteCaseDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
) : RemoteCaseDataSource {

    override suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, NetworkError> = runCatching {
        val resp = client.get("$baseUrl/games/${gameId.raw}/cases")
        // Explicit status check: the injected client does NOT set expectSuccess,
        // so Ktor never throws ClientRequestException/ServerResponseException —
        // a non-2xx would otherwise reach .body() and fail as a confusing
        // deserialization error. See PROBLEMS_PARLOR.md → content-01.
        if (!resp.status.isSuccess()) return Result.Failure(statusToError(resp.status))
        Result.Success(resp.body<List<CaseSummary>>()) as Result<List<CaseSummary>, NetworkError>
    }.getOrElse { failure ->
        failure.rethrowIfCancellation()
        failure.toNetworkErrorResult()
    }

    override suspend fun fetchCase(id: CaseId): Result<CaseEnvelope, NetworkError> = runCatching {
        val resp = client.get("$baseUrl/cases/${id.raw}")
        if (!resp.status.isSuccess()) return Result.Failure(statusToError(resp.status))
        Result.Success(resp.body<CaseEnvelope>()) as Result<CaseEnvelope, NetworkError>
    }.getOrElse { failure ->
        failure.rethrowIfCancellation()
        failure.toNetworkErrorResult()
    }

    private fun statusToError(status: HttpStatusCode): NetworkError =
        if (status.value == HttpStatusCode.Unauthorized.value) NetworkError.Unauthorized
        else NetworkError.Server(status.value)

    private fun <T> Throwable.toNetworkErrorResult(): Result<T, NetworkError> = when (this) {
        is HttpRequestTimeoutException -> Result.Failure(NetworkError.Timeout)
        is ClientRequestException -> {
            val code = response.status.value
            if (code == HttpStatusCode.Unauthorized.value) {
                Result.Failure(NetworkError.Unauthorized)
            } else {
                Result.Failure(NetworkError.Server(code))
            }
        }
        is ServerResponseException -> Result.Failure(NetworkError.Server(response.status.value))
        is SerializationException -> Result.Failure(NetworkError.Serialization(message ?: "decode"))
        else -> Result.Failure(NetworkError.Unknown(message))
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) throw this
    }
}
