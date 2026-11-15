package com.ricardofrango.squadify.domain.repository

/**
 * Export grants (TECHNICAL_DECISIONS §81): a short-lived, single-use permission for the other app's server to read
 * one of this user's age groups. Only the grant's hash is stored.
 */
interface ExportGrantRepository {
    suspend fun create(tokenHash: String, userId: String, expiresAtEpochMillis: Long)

    /** The user of the grant if it's valid at [nowEpochMillis] and unused, without using it (to list what it can read). */
    suspend fun userOf(tokenHash: String, nowEpochMillis: Long): String?

    /** The user of the grant if it's valid at [nowEpochMillis] and unused, marking it used; null otherwise. */
    suspend fun redeem(tokenHash: String, nowEpochMillis: Long): String?
}

/**
 * What an import remembers (TECHNICAL_DECISIONS §81): the age group it came from, and which record of the target
 * each source record became. Imported again, the same copy is updated instead of duplicated.
 */
interface ImportRepository {
    /** The target age group an earlier import of [sourceAgeGroupId] from [sourceServer] made in [targetClubId], if any. */
    suspend fun findAgeGroup(targetClubId: String, sourceServer: String, sourceAgeGroupId: String): String?

    /** The source age groups from [sourceServer] already imported into [targetClubId]. */
    suspend fun linkedAgeGroups(targetClubId: String, sourceServer: String): Set<String>

    suspend fun saveAgeGroupLink(targetClubId: String, sourceServer: String, sourceAgeGroupId: String, targetAgeGroupId: String)

    /** The target id a source record became, by its [kind] (TEAM, PLAYER, OPPONENT, MATCH...), if it was imported. */
    suspend fun findRecord(targetClubId: String, sourceServer: String, sourceAgeGroupId: String, kind: String, sourceId: String): String?

    suspend fun saveRecord(targetClubId: String, sourceServer: String, sourceAgeGroupId: String, kind: String, sourceId: String, targetId: String)
}
