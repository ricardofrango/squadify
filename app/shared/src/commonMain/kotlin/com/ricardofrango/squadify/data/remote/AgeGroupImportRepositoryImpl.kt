package com.ricardofrango.squadify.data.remote

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.AgeGroupImportRepository
import com.ricardofrango.squadify.model.ExportGrantResponse
import com.ricardofrango.squadify.model.ImportAgeGroupRequest
import com.ricardofrango.squadify.model.ImportAgeGroupResult
import com.ricardofrango.squadify.model.ImportableAgeGroup
import com.ricardofrango.squadify.model.ListSourceAgeGroupsRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AgeGroupImportRepositoryImpl(
    private val httpClient: HttpClient,
    private val appConfig: AppConfig
) : AgeGroupImportRepository {

    override suspend fun createExportGrant(): ExportGrantResponse =
        httpClient.post("${appConfig.baseUrl}/export/grants").handleResponse()

    override suspend fun listSourceAgeGroups(clubId: String, sourceUrl: String, grant: String): List<ImportableAgeGroup> =
        httpClient.post("${appConfig.baseUrl}/import/source/age-groups") {
            contentType(ContentType.Application.Json)
            setBody(ListSourceAgeGroupsRequest(clubId, sourceUrl, grant))
        }.handleResponse()

    override suspend fun importAgeGroup(clubId: String, sourceUrl: String, sourceAgeGroupId: String, grant: String): ImportAgeGroupResult =
        httpClient.post("${appConfig.baseUrl}/import/age-groups") {
            contentType(ContentType.Application.Json)
            setBody(ImportAgeGroupRequest(clubId, sourceUrl, sourceAgeGroupId, grant))
        }.handleResponse()
}
