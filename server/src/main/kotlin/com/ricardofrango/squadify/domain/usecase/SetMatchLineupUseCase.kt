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
 * Saves the live pitch view's arrangement -- the formation and who stands in each position -- and
 * sends it to every device watching the match. Before kickoff it's the coach placing the starters
 * (the game can only kick off once every position is filled, see StartMatchUseCase); during the game
 * it's a formation change or players swapping positions. Substitutions and red cards don't come
 * through here: each device works those out from the events (see currentPitchSlots). Same match-day
 * permission as kicking off.
 */
class SetMatchLineupUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val teamRepository: TeamRepository,
    private val liveMatchBus: LiveMatchBus,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String, request: SetMatchLineupRequest): MatchLineup = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minClubRole = ClubRole.COORDINATOR,
            configurablePermission = ConfigurablePermission.MANAGE_LIVE_MATCH,
            teamId = match.teamId,
            errorMessage = "Only admins, coordinators, coaches, or team staff can arrange the pitch"
        )

        if (match.status == MatchStatus.FINISHED || match.status == MatchStatus.CANCELLED) {
            throw ValidationException("The pitch can't be changed once the match is over")
        }

        val team = teamRepository.getTeamById(match.teamId) ?: throw NotFoundException("Team not found")
        val teamSize = team.teamSize ?: 11
        val formation = parseFormation(request.formation, teamSize)
            ?: throw ValidationException("The formation must add up to $teamSize players with the goalkeeper")

        val slots = request.slots
        if (slots.keys.any { it !in 0 until teamSize }) {
            throw ValidationException("A position must be one of the team's $teamSize")
        }
        if (slots.values.toSet().size != slots.size) {
            throw ValidationException("A player can only stand in one position")
        }
        val callUps = matchRepository.getCallUpsForMatch(matchId).map { it.callUp }
        if (!callUps.map { it.playerId }.toSet().containsAll(slots.values)) {
            throw ValidationException("Only players called up for this match can be on the pitch")
        }
        // Once the game is on, only the players on the pitch now (after substitutions and red cards)
        // can stand in a position; the bench comes on through a substitution.
        if (match.status != MatchStatus.SCHEDULED) {
            val events = matchEventRepository.getEventsByMatch(matchId)
            val onPitch = playerMinutes(match, callUps, events).filterValues { it.onPitch }.keys
            if (!onPitch.containsAll(slots.values)) {
                throw ValidationException("Only players on the pitch can be placed in a position")
            }
        }

        val lineup = MatchLineup(formation.formationText(), slots)
        matchRepository.setLineup(matchId, lineup.formation, slots)
        val score = matchEventRepository.getScoreByMatch(matchId)
        liveMatchBus.publish(matchId, LiveMatchUpdate(score = score, match = match.copy(formation = lineup.formation), lineup = lineup))
        lineup
    }
}
