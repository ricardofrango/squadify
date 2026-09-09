package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.UpdateMatchRequest

/**
 * Partial update of a scheduled match's own data (opponent, kick-off, location, notes) -- not its
 * status or call-ups, which have their own use cases. Same gate as creating one: a club
 * admin/coordinator or a coach of the match's age group.
 */
class UpdateMatchUseCase(
    private val matchRepository: MatchRepository,
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        request: UpdateMatchRequest
    ): Boolean = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minTeamRole = TeamRole.COACH,
            teamId = match.teamId,
            errorMessage = "Only admins, coordinators, or team coaches can edit a match"
        )

        request.opponentId?.let { opponentId ->
            val opponent = opponentRepository.getOpponentById(opponentId)
                ?: throw NotFoundException("Opponent not found")
            if (opponent.clubId != match.clubId) {
                throw ForbiddenException("Opponent does not belong to this club")
            }
        }

        val merged = match.copy(
            opponentId = request.opponentId ?: match.opponentId,
            dateTime = request.dateTime ?: match.dateTime,
            location = request.location ?: match.location,
            notes = request.notes ?: match.notes
        )
        matchRepository.updateMatch(merged)
    }
}
