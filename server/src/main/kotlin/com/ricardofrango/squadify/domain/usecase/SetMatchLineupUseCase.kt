package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.scope
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.*

/**
 * Saves the live pitch view's arrangement -- the formation and who stands in each position -- and
 * sends it to every device watching the match. Before kickoff it's the coach placing the starters
 * (the game can only kick off once every position is filled, see StartMatchUseCase); during the game
 * it's a formation change or players swapping positions, each also kept with its minute for the
 * finished match's timeline (MatchLineupChange). Substitutions and red cards don't come
 * through here: each device works those out from the events (see currentPitchSlots). Same match-day
 * permission as kicking off. Before kickoff it can also change how many a side the match is played
 * ([SetMatchLineupRequest.teamSize]), the formation and positions then being for the new count.
 */
class SetMatchLineupUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val liveMatchBus: LiveMatchBus,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val clubRepository: ClubRepository,
    private val clock: kotlin.time.Clock = kotlin.time.Clock.System
) {
    suspend operator fun invoke(userId: String, matchId: String, request: SetMatchLineupRequest): MatchLineup = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        accessPolicy.requireManageLiveMatch(userId, match.scope, "Only admins, coordinators, coaches, or team staff can arrange the pitch")

        if (match.status == MatchStatus.FINISHED || match.status == MatchStatus.CANCELLED) {
            throw ValidationException("The pitch can't be changed once the match is over")
        }

        val resizedTo = request.teamSize?.takeIf { it != match.teamSize }?.also { size ->
            if (match.status != MatchStatus.SCHEDULED) throw ConflictException("The team size can only be changed before kickoff")
            val club = clubRepository.getClubById(match.clubId) ?: throw NotFoundException("Club not found")
            requireMatchTeamSize(size, club)
        }
        val teamSize = resizedTo ?: match.teamSize
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
        val sized = if (resizedTo != null) match.copy(teamSize = resizedTo) else match
        if (resizedTo != null) matchRepository.updateMatch(sized)
        matchRepository.setLineup(matchId, lineup.formation, slots)
        // During the game each arrangement is kept, at the match clock's minute, for the finished
        // match's timeline. Its half is worked out like an event's (AddMatchEventUseCase).
        if (match.status == MatchStatus.LIVE || match.status == MatchStatus.HALF_TIME) {
            val secondHalf = match.status == MatchStatus.LIVE && match.secondHalfStartedAt != null
            val minute = if (match.status == MatchStatus.HALF_TIME) {
                match.firstHalfEndMinute ?: match.halfLengthMinutes ?: 0
            } else {
                elapsedMatchSeconds(match, clock.now()) / 60
            }
            matchRepository.addLineupChange(matchId, MatchLineupChange(maxOf(0, minute), if (secondHalf) 2 else 1, lineup.formation, slots, clock.now()))
        }
        val score = matchEventRepository.getScoreByMatch(matchId)
        liveMatchBus.publish(matchId, LiveMatchUpdate(score = score, match = sized.copy(formation = lineup.formation), lineup = lineup))
        lineup
    }
}
