package com.parlor.content.datasource

import com.parlor.content.schema.CaseEnvelope
import com.parlor.content.schema.CaseSummary
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.GameId
import com.parlor.core.result.NetworkError
import com.parlor.core.result.Result
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

internal const val MAX_CASE_LIST_RESPONSE_BYTES: Int = 256 * 1024
internal const val MAX_CASE_RESPONSE_BYTES: Int = 512 * 1024

/**
 * Optional Ktor-backed remote source for a future HTTPS content service.
 *
 * The current shipping application deliberately binds
 * [OfflineRemoteCaseDataSource]; this implementation is exercised by bounded
 * adapter tests but is not reachable from production DI. Enabling it is a
 * release-level decision requiring endpoint, trust, observability, and
 * compatibility review.
 */
class KtorRemoteCaseDataSource(
    private val client: HttpClient,
    private val baseUrl: String,
    private val json: Json = DEFAULT_JSON,
    private val decodeContext: CoroutineContext = Dispatchers.Default,
) : RemoteCaseDataSource {

    override suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, NetworkError> = try {
        if (!SAFE_CONTENT_ID.matches(gameId.raw)) {
            return Result.Failure(NetworkError.Serialization("invalid game id"))
        }
        val response = client.get("$baseUrl/games/${gameId.raw}/cases")
        // Explicit status check: the injected client does NOT set expectSuccess,
        // so Ktor never throws ClientRequestException/ServerResponseException —
        // a non-2xx would otherwise reach decoding and look like corrupt JSON.
        if (!response.status.isSuccess()) return Result.Failure(statusToError(response.status))
        val bytes = when (val body = response.readBounded(MAX_CASE_LIST_RESPONSE_BYTES)) {
            is Result.Success -> body.data
            is Result.Failure -> return body
        }
        val summaries = withContext(decodeContext) {
            json.decodeFromString<List<CaseSummary>>(bytes.decodeUtf8StrictForContent())
        }
        Result.Success(summaries)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        failure.toNetworkErrorResult()
    }

    override suspend fun fetchCase(id: CaseId): Result<CaseEnvelope, NetworkError> = try {
        if (!SAFE_CONTENT_ID.matches(id.raw)) {
            return Result.Failure(NetworkError.Serialization("invalid case id"))
        }
        val response = client.get("$baseUrl/cases/${id.raw}")
        if (!response.status.isSuccess()) return Result.Failure(statusToError(response.status))
        val bytes = when (val body = response.readBounded(MAX_CASE_RESPONSE_BYTES)) {
            is Result.Success -> body.data
            is Result.Failure -> return body
        }
        val envelope = withContext(decodeContext) {
            json.decodeFromString(CaseEnvelope.serializer(), bytes.decodeUtf8StrictForContent())
        }
        Result.Success(envelope)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        failure.toNetworkErrorResult()
    }

    private suspend fun HttpResponse.readBounded(
        maximumBytes: Int,
    ): Result<ByteArray, NetworkError> {
        val declaredLength = headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredLength != null && declaredLength > maximumBytes) {
            return Result.Failure(NetworkError.Serialization("response exceeds byte limit"))
        }
        // Read one byte beyond the policy limit so chunked/misreported bodies
        // cannot bypass the Content-Length precheck. The channel remains
        // streaming; the whole untrusted response is never accumulated.
        val bytes = bodyAsChannel()
            .readRemaining(maximumBytes.toLong() + 1L)
            .readByteArray()
        return if (bytes.size <= maximumBytes) {
            Result.Success(bytes)
        } else {
            bytes.fill(0)
            Result.Failure(NetworkError.Serialization("response exceeds byte limit"))
        }
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

    private companion object {
        val SAFE_CONTENT_ID = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = false
            isLenient = false
            encodeDefaults = true
        }
    }
}

private fun ByteArray.decodeUtf8StrictForContent(): String = try {
    decodeToString(throwOnInvalidSequence = true)
} catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
    throw SerializationException("response is not valid UTF-8", failure)
}
