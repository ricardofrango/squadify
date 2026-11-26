package com.sportssoftw.squadcall.data.remote

import com.sportssoftw.squadcall.domain.config.AppConfig
import com.sportssoftw.squadcall.domain.repository.FcmTokenRepository
import com.sportssoftw.squadcall.model.RegisterFcmTokenRequest
import com.sportssoftw.squadcall.model.RemoveFcmTokenRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class FcmTokenRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : FcmTokenRepository {

    override suspend fun registerToken(token: String) {
        httpClient.post("${appConfig.baseUrl}/fcm-tokens") {
            contentType(ContentType.Application.Json)
            setBody(RegisterFcmTokenRequest(token))
        }.handleResponse<Unit>()
    }

    override suspend fun removeToken(token: String) {
        httpClient.delete("${appConfig.baseUrl}/fcm-tokens") {
            contentType(ContentType.Application.Json)
            setBody(RemoveFcmTokenRequest(token))
        }.handleResponse<Unit>()
    }
}
