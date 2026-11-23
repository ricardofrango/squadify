package com.sportssoftw.squadify.data.remote

import com.sportssoftw.squadify.domain.config.AppConfig
import com.sportssoftw.squadify.domain.repository.FeatureFlagsRepository
import com.sportssoftw.squadify.model.FeatureFlags
import io.ktor.client.HttpClient
import io.ktor.client.request.get

class FeatureFlagsRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : FeatureFlagsRepository {

    override suspend fun load(): FeatureFlags = httpClient.get("${appConfig.baseUrl}/feature-flags").handleResponse()
}
