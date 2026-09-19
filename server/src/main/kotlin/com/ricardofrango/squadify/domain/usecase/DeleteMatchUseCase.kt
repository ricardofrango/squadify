package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.TeamRole

/**
 * Permanently removes a match, its call-ups, and its events. Coach-only, same as editing a match
 * (CreateMatchUseCase/UpdateMatchUseCase) -- deleting is a change to the match's own record, not a
 * match-day operation, so it's not open to staff (see TECHNICAL_DECISIONS.md's "Match-Day Permissions
 * Opened to Staff" section for that distinction).
 */
class DeleteMatchUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String): Boolean = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minTeamRole = TeamRole.COACH,
            teamId = match.teamId,
            errorMessage = "Only admins, coordinators, or team coaches can delete a match"
        )

        // No ON DELETE CASCADE on call-ups'/events' matchId foreign keys -- clear both before the
        // match row itself or the delete fails the FK constraint.
        matchEventRepository.deleteEventsByMatch(matchId)
        matchRepository.manageCallUps(matchId, emptyList())
        matchRepository.deleteMatch(matchId)
    }
}
