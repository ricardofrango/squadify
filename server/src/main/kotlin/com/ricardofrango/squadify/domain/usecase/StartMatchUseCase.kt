package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.*

/**
 * Kicks off a SCHEDULED match: locks in the Starting XI (the rest of the called-up squad becomes the
 * bench), the half length and the formation (also kept as the starting formation, for the match
 * report's tactic), keeps the starting arrangement for the finished match's timeline, then flips
 * the match to LIVE. This is the "KICK OFF" action on the
 * live match console's pre-kickoff screen -- see PROCESS_FLOWS.md.
 */
class StartMatchUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val teamRepository: TeamRepository,
    private val liveMatchBus: LiveMatchBus,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val notifyMatchEventUseCase: NotifyMatchEventUseCase,
    private val clubMatchesBus: ClubMatchesBus,
    private val transactionManager: TransactionManager,
    private val clock: kotlin.time.Clock = kotlin.time.Clock.System
) {
    suspend operator fun invoke(
        userId: String,
        matchId: String,
        request: StartMatchRequest
    ): Boolean {
        var updatedMatch: Match? = null

        val success = transactionManager.runInTransaction {
            val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

            checkUserPermissionUseCase(
                userId = userId,
                clubId = match.clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.MANAGE_LIVE_MATCH,
                teamId = match.teamId,
                errorMessage = "Only admins, coordinators, coaches, or team staff can start a match"
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
            val calledUp = matchRepository.getCallUpsForMatch(matchId)
            val callUps = calledUp.map { it.callUp }
            val eligiblePlayerIds = callUps.map { it.playerId }.toSet()
            if (!eligiblePlayerIds.containsAll(startingIds)) {
                throw ValidationException("The starting line-up can only include players called up for this match")
            }
            // With the pitch view the starters are the players placed on the pitch (SetMatchLineupUseCase);
            // an app from before it places nobody, and its starters stand as they are.
            val placedIds = callUps.filter { it.pitchSlot != null }.map { it.playerId }.toSet()
            if (placedIds.isNotEmpty() && placedIds != startingIds.toSet()) {
                throw ValidationException("The starting line-up must be the players placed on the pitch")
            }
            val formation = request.formation
                ?.let { parseFormation(it, requiredCount) ?: throw ValidationException("The formation must add up to $requiredCount players with the goalkeeper") }
                ?: matchFormation(match.formation, requiredCount)

            val started = matchRepository.startMatch(matchId, startingIds, request.halfLengthMinutes, formation.formationText())
            if (started) {
                // The side that kicks off is the timeline's first entry (MatchLineupChange): as placed on
                // the pitch, or, from an app before the pitch view, the starters by their positions.
                val placed = callUps.filter { it.pitchSlot != null && it.playerId in startingIds }.associate { it.pitchSlot!! to it.playerId }
                val startingSlots = placed.ifEmpty {
                    autoArrangeSlots(formation, startingIds.map { id -> id to calledUp.firstOrNull { it.player.id == id }?.player?.position })
                }
                matchRepository.addLineupChange(matchId, MatchLineupChange(0, 1, formation.formationText(), startingSlots, clock.now()))
                // Re-fetch (kickoffAt/halfLengthMinutes were just set by startMatch above, not
                // available on the pre-kickoff `match` read at the top) -- must happen inside this
                // transaction, since a MatchRepository call made outside one throws. Shared by both
                // broadcasts below.
                val refetched = matchRepository.getMatchById(matchId)
                updatedMatch = refetched
                val score = matchEventRepository.getScoreByMatch(matchId)
                liveMatchBus.publish(matchId, LiveMatchUpdate(score = score, match = refetched))
            }
            started
        }

        // Sent after the transaction commits -- a failed/slow push must never roll back kickoff.
        if (success) {
            notifyMatchEventUseCase(matchId, MatchNotificationEvent.KICKOFF)
            updatedMatch?.let { clubMatchesBus.publish(it.clubId, ClubMatchesUpdate(match = it)) }
        }
        return success
    }
}
