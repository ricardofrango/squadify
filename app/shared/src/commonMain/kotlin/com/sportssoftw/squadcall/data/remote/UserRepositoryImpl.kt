package com.sportssoftw.squadcall.data.remote

import com.sportssoftw.squadcall.data.cache.cachedGet
import com.sportssoftw.squadcall.domain.config.AppConfig
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.model.ChangePasswordRequest
import com.sportssoftw.squadcall.model.EmailChangeConfirmRequest
import com.sportssoftw.squadcall.model.EmailChangeRequest
import com.sportssoftw.squadcall.model.PhotoResponse
import com.sportssoftw.squadcall.model.UpdateProfileRequest
import com.sportssoftw.squadcall.model.User
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.ByteArrayContent

class UserRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : UserRepository {

    override suspend fun getCurrentUser(): User =
        httpClient.cachedGet("${appConfig.baseUrl}/users/me")

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
