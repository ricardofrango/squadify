package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.model.ErrorResponse
import com.ricardofrango.squadify.model.PLAN_LIMIT_CODE
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Thrown by [handleResponse] for any non-2xx response, carrying the HTTP status and the server's
 * error [code] (see `TECHNICAL_DECISIONS.md` §4's exception-to-code table, e.g. "DATA_001" for a
 * conflict) so callers can branch on *what kind* of failure this was instead of pattern-matching the
 * message text. [message] is still the server's copy -- callers that only need to display *something*
 * generic can fall back to it, but per `AI_GUIDELINES.md` "No Raw Exception Messages in UI", screens
 * should prefer their own localized copy and reserve this for cases that need to key off [statusCode]
 * or [code] specifically (e.g. a 409 conflict getting special handling).
 */
class ApiException(
    val statusCode: Int,
    val code: String?,
    override val message: String,
    // For a batch request: which items the error is about (ErrorResponse.rows).
    val rows: List<Int>? = null
) : Exception(message)

/** The server refused it as not on the space's plan (the coach server's Free plan, REQUIREMENTS §5c). */
val Throwable.isNotInPlan: Boolean get() = this is ApiException && statusCode == 403 && code == PLAN_LIMIT_CODE

suspend inline fun <reified T> HttpResponse.handleResponse(): T {
    if (status.isSuccess()) {
        return body()
    } else {
        val errorBody = try {
            body<ErrorResponse>()
        } catch (e: Exception) {
            null
        }

        throw ApiException(
            statusCode = status.value,
            code = errorBody?.code,
            message = errorBody?.message ?: "An unexpected error occurred (Status: ${status.value})",
            rows = errorBody?.rows
        )
    }
}

/**
 * A request that never got a response: no connection, the server down, or (on web) a request the
 * browser blocked. On web, Ktor's fetch engine reports this as a `kotlin.Error` ("Fail to fetch"), which
 * isn't an [Exception], so it slipped past every view model's `catch (e: Exception)` and crashed the
 * app. [mapNetworkErrors] turns it into this instead.
 */
class NetworkException(cause: Throwable) : Exception(cause.message ?: "Network request failed", cause)

/** Rethrows anything a request throws that isn't an [Exception] as a [NetworkException]. */
fun HttpClientConfig<*>.mapNetworkErrors() {
    HttpResponseValidator {
        handleResponseExceptionWithRequest { cause, _ ->
            if (cause !is Exception) throw NetworkException(cause)
        }
    }
}
