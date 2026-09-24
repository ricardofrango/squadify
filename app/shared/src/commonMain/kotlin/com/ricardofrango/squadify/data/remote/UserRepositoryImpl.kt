package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.model.ChangePasswordRequest
import com.ricardofrango.squadify.model.UpdateProfileRequest
import com.ricardofrango.squadify.model.User
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class UserRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig,
    private val sessionRepository: SessionRepository
) : UserRepository {

    private suspend fun HttpRequestBuilder.auth() {
        sessionRepository.getToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun getCurrentUser(): User =
        httpClient.get("${appConfig.baseUrl}/users/me") { auth() }.handleResponse()

    override suspend fun updateProfile(request: UpdateProfileRequest): User =
        httpClient.patch("${appConfig.baseUrl}/users/me") {
            contentType(ContentType.Application.Json); setBody(request); auth()
        }.handleResponse()

    override suspend fun changePassword(request: ChangePasswordRequest) {
        httpClient.post("${appConfig.baseUrl}/users/me/password") {
            contentType(ContentType.Application.Json); setBody(request); auth()
        }.handleResponse<Unit>()
    }
}
