package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.coach.domain.AccountDeletionRepository
import com.ricardofrango.squadify.data.remote.handleResponse
import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.model.AccountDeletionSummary
import com.ricardofrango.squadify.model.DeleteAccountRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AccountDeletionRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : AccountDeletionRepository {

    override suspend fun getSummary(): AccountDeletionSummary =
        httpClient.get("${appConfig.baseUrl}/users/me/deletion").handleResponse()

    override suspend fun deleteAccount(currentPassword: String) {
        httpClient.post("${appConfig.baseUrl}/users/me/delete") {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(currentPassword))
        }.handleResponse<Unit>()
    }
}
