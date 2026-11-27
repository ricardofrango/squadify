package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.scope
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.service.ClubMatchesBus
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ConflictException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.ClubMatchesUpdate
import com.sportssoftw.squadcall.model.MatchStatus

/**
 * Permanently removes a match, its call-ups, and its events (cascaded by the database, V21). Coach-only, same as editing a match
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
    private val accessPolicy: AccessPolicy,
    private val clubMatchesBus: ClubMatchesBus,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String): Boolean {
        var clubId: String? = null

        val success = transactionManager.runInTransaction {
            val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")
            clubId = match.clubId

            accessPolicy.requireManageMatch(userId, match.scope, "Only admins, coordinators, or team coaches can delete a match")

            if (match.status == MatchStatus.FINISHED) {
                throw ConflictException("A match that has been played can't be deleted")
            }

            // ON DELETE CASCADE (V21) takes its call-ups and events with it.
            matchRepository.deleteMatch(matchId)
        }

        // Sent after the transaction commits -- a failed/slow broadcast must never roll back the delete.
        if (success) {
            clubId?.let { clubMatchesBus.publish(it, ClubMatchesUpdate(deletedMatchId = matchId)) }
        }
        return success
    }
}
