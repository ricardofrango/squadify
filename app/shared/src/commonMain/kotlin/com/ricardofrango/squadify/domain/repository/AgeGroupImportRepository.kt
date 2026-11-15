package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.ExportGrantResponse
import com.ricardofrango.squadify.model.ImportAgeGroupResult
import com.ricardofrango.squadify.model.ImportableAgeGroup

/**
 * Copying an age group between the clubs and coach apps (TECHNICAL_DECISIONS §81). The grant comes from the app
 * that has the age group (its own server, the signed-in user); the rest is on the importing app's server.
 */
interface AgeGroupImportRepository {
    /** The exporting app's server: a grant for the signed-in user to read one of their age groups once. */
    suspend fun createExportGrant(): ExportGrantResponse

    /** The age groups [grant] offers, for [clubId]: which of them this club already has. */
    suspend fun listSourceAgeGroups(clubId: String, sourceUrl: String, grant: String): List<ImportableAgeGroup>

    /** Copies one age group into [clubId]; the grant is used up. */
    suspend fun importAgeGroup(clubId: String, sourceUrl: String, sourceAgeGroupId: String, grant: String): ImportAgeGroupResult
}
