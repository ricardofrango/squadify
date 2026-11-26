package com.sportssoftw.squadcall.data.remote

import com.sportssoftw.squadcall.domain.config.AppConfig
import com.sportssoftw.squadcall.domain.repository.FeatureFlagsRepository
import com.sportssoftw.squadcall.model.FeatureFlags
import io.ktor.client.HttpClient
import io.ktor.client.request.get

class FeatureFlagsRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : FeatureFlagsRepository {

    override suspend fun load(): FeatureFlags = httpClient.get("${appConfig.baseUrl}/feature-flags").handleResponse()
}
