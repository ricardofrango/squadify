package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.coach.domain.SpaceRepository
import com.ricardofrango.squadify.data.remote.handleResponse
import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceSetup
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class SpaceRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : SpaceRepository {

    override suspend fun getSpace(): CoachSpace =
        httpClient.get("${appConfig.baseUrl}/space").handleResponse()

    override suspend fun setUpSpace(request: SetUpSpaceRequest): SpaceSetup =
        httpClient.post("${appConfig.baseUrl}/space") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.handleResponse()
}
