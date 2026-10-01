package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.MatchStatus

/**
 * Permanently removes a match, its call-ups, and its events. Coach-only, same as editing a match
 * (CreateMatchUseCase/UpdateMatchUseCase) -- deleting is a change to the match's own record, not a
 * match-day operation, so it's not open to staff (see TECHNICAL_DECISIONS.md's "Match-Day Permissions
 * Opened to Staff" section for that distinction).
 *
 * A match that was actually played (FINISHED) is a permanent record -- its events, player stats and
 * report all hang off it -- so it can't be deleted. Anything else (scheduled, even past its date,
 * cancelled, or not yet ended) still can.
 */
class DeleteMatchUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val clubMatchesBus: ClubMatchesBus,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String): Boolean {
        var clubId: String? = null

        val success = transactionManager.runInTransaction {
            val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")
            clubId = match.clubId

            checkUserPermissionUseCase(
                userId = userId,
                clubId = match.clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.MANAGE_MATCH,
                teamId = match.teamId,
                errorMessage = "Only admins, coordinators, or team coaches can delete a match"
            )

            if (match.status == MatchStatus.FINISHED) {
                throw ConflictException("A match that has been played can't be deleted")
            }

            // No ON DELETE CASCADE on call-ups'/events' matchId foreign keys -- clear both before the
            // match row itself or the delete fails the FK constraint.
            matchEventRepository.deleteEventsByMatch(matchId)
            matchRepository.manageCallUps(matchId, emptyList())
            matchRepository.deleteMatch(matchId)
        }

        // Sent after the transaction commits -- a failed/slow broadcast must never roll back the delete.
        if (success) {
            clubId?.let { clubMatchesBus.publish(it, ClubMatchesUpdate(deletedMatchId = matchId)) }
        }
        return success
    }
}
