package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.MatchReports
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.model.MatchReport
import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.uuid.Uuid

class ExposedMatchReportRepository : MatchReportRepository {

    override suspend fun getByMatch(matchId: String): MatchReport? =
        MatchReports.selectAll()
            .where { MatchReports.matchId eq matchId.toUuid() }
            .singleOrNull()
            ?.let(::toReport)

    override suspend fun upsert(report: MatchReport) {
        MatchReports.upsert(
            MatchReports.matchId,
            // Keep the row's original id on update; everything else is replaced.
            onUpdateExclude = listOf(MatchReports.id)
        ) {
            it[id] = Uuid.random()
            it[matchId] = report.matchId.toUuid()
            it[tactic] = report.tactic
            it[positiveAspects] = report.positiveAspects
            it[lessPositiveAspects] = report.lessPositiveAspects
            it[aspectsToImprove] = report.aspectsToImprove
            it[teamNotes] = report.teamNotes
            it[opponentStrengths] = report.opponentStrengths
            it[opponentWeaknesses] = report.opponentWeaknesses
            it[opponentBestPlayers] = report.opponentBestPlayers
            it[opponentAdditionalInfo] = report.opponentAdditionalInfo
            it[completedAt] = report.completedAt
            it[updatedAt] = report.updatedAt
            it[updatedBy] = report.updatedByUserId.toUuid()
        }
    }

    override suspend fun getCompletedMatchIds(matchIds: List<String>): Set<String> {
        if (matchIds.isEmpty()) return emptySet()
        return MatchReports.select(MatchReports.matchId)
            .where { (MatchReports.matchId inList matchIds.map { it.toUuid() }) and MatchReports.completedAt.isNotNull() }
            .map { it[MatchReports.matchId].toString() }
            .toSet()
    }

    private fun toReport(row: ResultRow) = MatchReport(
        matchId = row[MatchReports.matchId].toString(),
        tactic = row[MatchReports.tactic],
        positiveAspects = row[MatchReports.positiveAspects],
        lessPositiveAspects = row[MatchReports.lessPositiveAspects],
        aspectsToImprove = row[MatchReports.aspectsToImprove],
        teamNotes = row[MatchReports.teamNotes],
        opponentStrengths = row[MatchReports.opponentStrengths],
        opponentWeaknesses = row[MatchReports.opponentWeaknesses],
        opponentBestPlayers = row[MatchReports.opponentBestPlayers],
        opponentAdditionalInfo = row[MatchReports.opponentAdditionalInfo],
        completedAt = row[MatchReports.completedAt],
        updatedAt = row[MatchReports.updatedAt],
        updatedByUserId = row[MatchReports.updatedBy].toString()
    )
}
