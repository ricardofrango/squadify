package com.ricardofrango.clubmanager.data.remote

import com.ricardofrango.clubmanager.model.ErrorResponse
import io.ktor.client.call.*
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
    override val message: String
) : Exception(message)

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
            message = errorBody?.message ?: "An unexpected error occurred (Status: ${status.value})"
        )
    }
}
