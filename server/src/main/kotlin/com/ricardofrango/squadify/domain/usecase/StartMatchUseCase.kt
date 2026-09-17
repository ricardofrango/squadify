package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.*

/**
 * Kicks off a SCHEDULED match: locks in the Starting XI (the rest of the called-up squad becomes the
 * bench) and the half length, then flips the match to LIVE. This is the "KICK OFF" action on the
 * live match console's pre-kickoff screen -- see PROCESS_FLOWS.md.
 */
class StartMatchUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val teamRepository: TeamRepository,
    private val liveMatchBus: LiveMatchBus,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        request: StartMatchRequest
    ): Boolean = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minTeamRole = TeamRole.COACH,
            teamId = match.teamId,
            errorMessage = "Only admins, coordinators, or team coaches can start a match"
        )

        if (match.status != MatchStatus.SCHEDULED) {
            throw ValidationException("Match has already started")
        }
        if (request.halfLengthMinutes <= 0) {
            throw ValidationException("Half length must be a positive number of minutes")
        }
        val startingIds = request.startingPlayerIds
        if (startingIds.isEmpty()) {
            throw ValidationException("Pick a starting line-up before kicking off")
        }
        if (startingIds.toSet().size != startingIds.size) {
            throw ValidationException("A player can only be in the starting line-up once")
        }

        // The a-side count this team plays -- falls back to 11 for teams recorded before team sizes
        // existed (see PROCESS_FLOWS.md's team-size flows).
        val team = teamRepository.getTeamById(match.teamId) ?: throw NotFoundException("Team not found")
        val requiredCount = team.teamSize ?: 11
        if (startingIds.size != requiredCount) {
            throw ValidationException("The starting line-up needs exactly $requiredCount players")
        }

        // Every call-up is CALLED_UP -- there's no other status to filter on (see CallUpStatus).
        val eligiblePlayerIds = matchRepository.getCallUpsForMatch(matchId)
            .map { it.callUp.playerId }
            .toSet()
        if (!eligiblePlayerIds.containsAll(startingIds)) {
            throw ValidationException("The starting line-up can only include players called up for this match")
        }

        val success = matchRepository.startMatch(matchId, startingIds, request.halfLengthMinutes)
        if (success) {
            val score = matchEventRepository.getScoreByMatch(matchId)
            liveMatchBus.publish(matchId, LiveMatchUpdate(score = score, status = MatchStatus.LIVE))
        }
        success
    }
}
