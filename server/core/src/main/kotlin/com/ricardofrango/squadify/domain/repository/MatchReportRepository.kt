package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.MatchReport

interface MatchReportRepository {
    suspend fun getByMatch(matchId: String): MatchReport?
    // Inserts or replaces the match's single report row (unique on match_id).
    suspend fun upsert(report: MatchReport)
    // Of [matchIds], those whose report has been completed -- one query, for the Home tab's to-do.
    suspend fun getCompletedMatchIds(matchIds: List<String>): Set<String>
}
