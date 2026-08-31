package com.ricardofrango.footballmanager.data.remote

import com.ricardofrango.footballmanager.domain.config.AppConfig
import com.ricardofrango.footballmanager.domain.repository.AuthRepository
import com.ricardofrango.footballmanager.model.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
        }.body()
    }

    override suspend fun register(request: RegisterRequest): User {
        return httpClient.post("${appConfig.baseUrl}/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun refreshToken(request: RefreshTokenRequest): TokenResponse {
        return httpClient.post("${appConfig.baseUrl}/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
