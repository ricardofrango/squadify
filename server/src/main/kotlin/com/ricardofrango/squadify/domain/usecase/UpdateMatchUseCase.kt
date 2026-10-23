package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.MatchVenue
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.UpdateMatchRequest

/**
 * Partial update of a scheduled match's own data (opponent, kick-off, location, notes) -- not its
 * status or call-ups, which have their own use cases. Same gate as creating one: a club
 * admin/coordinator or a coach of the match's age group. A finished or cancelled match is a closed
 * record and can't be edited at all (type included).
 */
class UpdateMatchUseCase(
    private val matchRepository: MatchRepository,
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val notifyMatchEventUseCase: NotifyMatchEventUseCase,
    private val clubMatchesBus: ClubMatchesBus,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        request: UpdateMatchRequest
    ): Boolean {
        var scheduleChanged = false
        var updated: Match? = null

        val success = transactionManager.runInTransaction {
            val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

            checkUserPermissionUseCase(
                userId = userId,
                clubId = match.clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.MANAGE_MATCH,
                teamId = match.teamId,
                errorMessage = "Only admins, coordinators, or team coaches can edit a match"
            )

            if (match.status == MatchStatus.FINISHED || match.status == MatchStatus.CANCELLED) {
                throw ConflictException("A finished or cancelled match can't be edited")
            }

            request.halves?.let { halves ->
                if (halves !in 1..2) throw ValidationException("A match is played in 1 or 2 halves")
                // The format drives the live console (half time or not), so it's fixed once play starts.
                if (halves != match.halves && match.status != MatchStatus.SCHEDULED) {
                    throw ConflictException("The number of halves can only be changed before kickoff")
                }
            }

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
                // Null keeps it; blank clears it (a match moved to neutral ground has none).
                location = request.location.let { if (it != null) it.trim().ifBlank { null } else match.location },
                notes = request.notes ?: match.notes,
                type = request.type ?: match.type,
                halves = request.halves ?: match.halves,
                venue = request.venue ?: match.venue
            )

            // Only the schedule-relevant fields warrant a "match updated" push -- a notes-only edit
            // isn't a schedule change (see TECHNICAL_DECISIONS.md's push-notifications section).
            scheduleChanged = merged.opponentId != match.opponentId ||
                merged.dateTime != match.dateTime ||
                merged.location != match.location

            updated = merged

            matchRepository.updateMatch(merged)
        }

        // Sent after the transaction commits -- a failed/slow push must never roll back the update.
        if (success) {
            if (scheduleChanged) {
                notifyMatchEventUseCase(matchId, MatchNotificationEvent.RESCHEDULED)
            }
            updated?.let { clubMatchesBus.publish(it.clubId, ClubMatchesUpdate(match = it)) }
        }
        return success
    }
}
