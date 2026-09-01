package com.ricardofrango.footballmanager.data.remote

import com.ricardofrango.footballmanager.model.ErrorResponse
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*

suspend inline fun <reified T> HttpResponse.handleResponse(): T {
    if (status.isSuccess()) {
        return body()
    } else {
        val errorBody = try {
            body<ErrorResponse>()
        } catch (e: Exception) {
            null
        }
        
        val message = errorBody?.message ?: "An unexpected error occurred (Status: ${status.value})"
        throw Exception(message)
    }
}
