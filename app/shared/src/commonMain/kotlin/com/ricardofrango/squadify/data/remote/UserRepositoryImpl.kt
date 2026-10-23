package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.model.ChangePasswordRequest
import com.ricardofrango.squadify.model.EmailChangeConfirmRequest
import com.ricardofrango.squadify.model.EmailChangeRequest
import com.ricardofrango.squadify.model.PhotoResponse
import com.ricardofrango.squadify.model.UpdateProfileRequest
import com.ricardofrango.squadify.model.User
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.ByteArrayContent

class UserRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : UserRepository {

    override suspend fun getCurrentUser(): User =
        httpClient.get("${appConfig.baseUrl}/users/me").handleResponse()

    override suspend fun updateProfile(request: UpdateProfileRequest): User =
        httpClient.patch("${appConfig.baseUrl}/users/me") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun changePassword(request: ChangePasswordRequest) {
        httpClient.post("${appConfig.baseUrl}/users/me/password") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }

    override suspend fun requestEmailChange(request: EmailChangeRequest) {
        httpClient.post("${appConfig.baseUrl}/users/me/email/request") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }

    override suspend fun confirmEmailChange(request: EmailChangeConfirmRequest): User =
        httpClient.post("${appConfig.baseUrl}/users/me/email/confirm") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()

    override suspend fun uploadAvatar(bytes: ByteArray): String =
        httpClient.put("${appConfig.baseUrl}/users/me/avatar") {
            setBody(ByteArrayContent(bytes, ContentType.Application.OctetStream))
        }.handleResponse<PhotoResponse>().photoUrl

    override suspend fun deleteAvatar() {
        httpClient.delete("${appConfig.baseUrl}/users/me/avatar").handleResponse<Unit>()
    }
}
