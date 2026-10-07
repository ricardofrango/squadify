package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.AuthRepository
import com.ricardofrango.squadify.model.*
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AuthRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : AuthRepository {

    override suspend fun login(request: LoginRequest): LoginResponse {
        return httpClient.post("${appConfig.baseUrl}/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()
    }

    override suspend fun register(request: RegisterRequest): User {
        return httpClient.post("${appConfig.baseUrl}/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()
    }

    override suspend fun refreshToken(request: RefreshTokenRequest): TokenResponse {
        return httpClient.post("${appConfig.baseUrl}/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()
    }

    override suspend fun requestPasswordReset(request: PasswordResetRequest) {
        httpClient.post("${appConfig.baseUrl}/auth/password-reset/request") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }

    override suspend fun confirmPasswordReset(request: PasswordResetConfirmRequest) {
        httpClient.post("${appConfig.baseUrl}/auth/password-reset/confirm") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse<Unit>()
    }
}
