package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.MatchReport
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.SaveMatchReportRequest
import kotlin.time.Clock

/**
 * Saves a finished match's report text and the per-player ratings (0-10) in one write.
 *
 * Gated by MANAGE_MATCH, the same configurable permission as scheduling and editing the match
 * (Coach by default), with the usual coordinator/admin floor. Blank text is stored as null.
 * `complete = true` marks the report done, taking it off the Home tab's to-do. A completed report
 * stays completed and can still be edited.
 */
class SaveMatchReportUseCase(
    private val matchRepository: MatchRepository,
    private val matchReportRepository: MatchReportRepository,
    private val matchPlayerStatsRepository: MatchPlayerStatsRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String, request: SaveMatchReportRequest): MatchReport =
        transactionManager.runInTransaction {
            val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

            checkUserPermissionUseCase(
                userId = userId,
                clubId = match.clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.MANAGE_MATCH,
                teamId = match.teamId,
                errorMessage = "Only admins, coordinators, or this team's coaches can write the match report"
            )

            if (match.status != MatchStatus.FINISHED) {
                throw ValidationException("A match report can only be written once the match has finished")
            }

            val ratedIds = request.ratings.map { it.playerId }
            if (ratedIds.size != ratedIds.toSet().size) {
                throw ValidationException("Each player can only be rated once")
            }
            if (request.ratings.any { r -> r.rating != null && r.rating !in 0..10 }) {
                throw ValidationException("Ratings must be between 0 and 10")
            }
            if (ratedIds.isNotEmpty()) {
                val statsPlayerIds = matchPlayerStatsRepository.getByMatch(matchId).map { it.playerId }.toSet()
                if (!statsPlayerIds.containsAll(ratedIds)) {
                    throw ValidationException("Only players called up for this match can be rated")
                }
            }

            val existing = matchReportRepository.getByMatch(matchId)
            val now = Clock.System.now()
            val report = MatchReport(
                matchId = matchId,
                tactic = request.tactic.clean(),
                positiveAspects = request.positiveAspects.clean(),
                lessPositiveAspects = request.lessPositiveAspects.clean(),
                aspectsToImprove = request.aspectsToImprove.clean(),
                teamNotes = request.teamNotes.clean(),
                opponentStrengths = request.opponentStrengths.clean(),
                opponentWeaknesses = request.opponentWeaknesses.clean(),
                opponentBestPlayers = request.opponentBestPlayers.clean(),
                opponentAdditionalInfo = request.opponentAdditionalInfo.clean(),
                completedAt = existing?.completedAt ?: if (request.complete) now else null,
                updatedAt = now,
                updatedByUserId = userId
            )
            matchReportRepository.upsert(report)
            if (request.ratings.isNotEmpty()) {
                matchPlayerStatsRepository.updateRatings(matchId, request.ratings.associate { it.playerId to it.rating })
            }
            report
        }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
