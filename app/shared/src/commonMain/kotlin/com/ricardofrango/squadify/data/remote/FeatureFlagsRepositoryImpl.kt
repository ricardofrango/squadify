package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.FeatureFlagsRepository
import com.ricardofrango.squadify.model.FeatureFlags
import io.ktor.client.HttpClient
import io.ktor.client.request.get

class FeatureFlagsRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : FeatureFlagsRepository {

    override suspend fun load(): FeatureFlags = httpClient.get("${appConfig.baseUrl}/feature-flags").handleResponse()
}
