package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.domain.stats.MatchPlayerStatsCalculator
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

// The longest half the record accepts (the live console's longest is 45').
private const val MAX_HALF_LENGTH_MINUTES = 60

// Event minutes run on the match clock, stoppage time included: well past the halves, never this far.
private const val MAX_EVENT_MINUTE = 150

private val RECORD_EVENT_TYPES = setOf(EventType.GOAL, EventType.OWN_GOAL, EventType.YELLOW_CARD, EventType.RED_CARD)

/**
 * A match's record after the game (TECHNICAL_DECISIONS.md §70), for any of the age group's staff
 * (MANAGE_LIVE_MATCH). Two cases:
 * - **A game followed live** (FINISHED): its goals, own goals, assists and cards can be corrected, e.g.
 *   a goal logged without the scorer, or a card forgotten. Substitutions and who started stay as they
 *   were, and so does every player's minutes: they're what happened on the pitch.
 * - **A game not followed live** (no battery, no signal): once its kickoff time has passed, a SCHEDULED
 *   match can have its result entered, its goals and cards plus each called-up player's minutes,
 *   typed in. It becomes FINISHED ([Match.enteredAfterGame]) with a single "final result" push, and
 *   stays editable the same way, minutes included. It never has substitutions or a timeline.
 *
 * Either way the record stays editable, and each save rebuilds the players' stats lines, keeping
 * their match report ratings.
 */
class SaveMatchRecordUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val matchPlayerStatsRepository: MatchPlayerStatsRepository,
    private val liveMatchBus: LiveMatchBus,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val notifyMatchEventUseCase: NotifyMatchEventUseCase,
    private val clubMatchesBus: ClubMatchesBus,
    private val transactionManager: TransactionManager,
    private val now: () -> Instant = { Clock.System.now() }
) {
    suspend operator fun invoke(userId: String, matchId: String, request: SaveMatchRecordRequest): Match {
        var firstEntry = false
        lateinit var score: MatchScore
        val saved = transactionManager.runInTransaction {
            val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")
            checkUserPermissionUseCase(
                userId = userId,
                clubId = match.clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.MANAGE_LIVE_MATCH,
                teamId = match.teamId,
                errorMessage = "Insufficient permissions to edit this match's record"
            )

            val afterGame = when (match.status) {
                MatchStatus.FINISHED -> match.enteredAfterGame
                MatchStatus.SCHEDULED -> {
                    if (match.dateTime > now()) throw ValidationException("A match's result can only be entered once it has kicked off")
                    true
                }
                else -> throw ValidationException("Only a finished match, or one that wasn't followed live, has a record to edit")
            }
            firstEntry = match.status == MatchStatus.SCHEDULED

            val callUps = matchRepository.getCallUpsForMatch(matchId).map { it.callUp }
            val calledUp = callUps.map { it.playerId }.toSet()
            if (afterGame && callUps.isEmpty()) throw ValidationException("Save the call-up before entering the result")

            val halfLength = if (afterGame) {
                val length = request.halfLengthMinutes ?: match.halfLengthMinutes
                    ?: throw ValidationException("The half length is required")
                if (length !in 1..MAX_HALF_LENGTH_MINUTES) throw ValidationException("A half is 1 to $MAX_HALF_LENGTH_MINUTES minutes long")
                length
            } else {
                if (request.halfLengthMinutes != null || request.minutesPlayed.isNotEmpty()) {
                    throw ValidationException("A match followed live keeps the minutes it had")
                }
                null
            }
            if (halfLength != null) {
                request.minutesPlayed.forEach { (playerId, minutes) ->
                    if (playerId !in calledUp) throw ValidationException("Only called-up players have minutes")
                    if (minutes !in 0..halfLength * match.halves) throw ValidationException("Minutes played can't be more than the match")
                }
            }

            val existing = matchEventRepository.getEventsByMatch(matchId)
            val oldStats = matchPlayerStatsRepository.getByMatch(matchId)
            // Who was on the pitch at some point, so who can score or assist.
            val played: Set<String> = if (halfLength != null) {
                request.minutesPlayed.filterValues { it > 0 }.keys
            } else {
                // Coming on in the last minute still counts, with 0 minutes.
                oldStats.filter { it.minutesPlayed > 0 || it.started }.map { it.playerId }.toSet() +
                    existing.filter { it.type == EventType.SUBSTITUTION_IN }.mapNotNull { it.playerId }
            }
            validate(request.events, match, existing, calledUp, played, statsKnown = halfLength != null || oldStats.isNotEmpty())

            applyEvents(matchId, request.events, existing)

            val recorded = if (halfLength != null) {
                matchRepository.markEnteredAfterGame(matchId, halfLength, match.halves)
                match.copy(
                    status = MatchStatus.FINISHED,
                    enteredAfterGame = true,
                    halfLengthMinutes = halfLength,
                    firstHalfEndMinute = if (match.halves == 2) halfLength else null,
                    fullTimeMinute = halfLength * match.halves,
                    pausedAt = null
                )
            } else {
                match
            }

            // A match finished before post-match stats existed has none, and still gets none.
            if (recorded.fullTimeMinute != null) {
                val counted = MatchPlayerStatsCalculator.calculate(recorded, callUps, matchEventRepository.getEventsByMatch(matchId))
                val previous = oldStats.associateBy { it.playerId }
                matchPlayerStatsRepository.replaceForMatch(matchId, counted.map { line ->
                    val before = previous[line.playerId]
                    when {
                        halfLength != null -> line.copy(started = false, minutesPlayed = request.minutesPlayed[line.playerId] ?: 0, rating = before?.rating)
                        before != null -> line.copy(started = before.started, minutesPlayed = before.minutesPlayed, rating = before.rating)
                        else -> line
                    }
                })
            }

            score = matchEventRepository.getScoreByMatch(matchId)
            liveMatchBus.publish(matchId, LiveMatchUpdate(score = score, match = recorded))
            recorded
        }

        // After the commit, as every push: one "final result" when a game not followed live is entered.
        if (firstEntry) {
            notifyMatchEventUseCase(matchId, MatchNotificationEvent.FULL_TIME)
            clubMatchesBus.publish(saved.clubId, ClubMatchesUpdate(match = saved))
        } else {
            clubMatchesBus.publish(saved.clubId, ClubMatchesUpdate(score = score))
        }
        return saved
    }

    private fun validate(
        events: List<MatchRecordEvent>,
        match: Match,
        existing: List<MatchEvent>,
        calledUp: Set<String>,
        played: Set<String>,
        statsKnown: Boolean
    ) {
        val editable = existing.filter(::isEditable).associateBy { it.id }
        val ids = events.mapNotNull { it.id }
        if (ids.size != ids.toSet().size) throw ValidationException("An event is listed twice")
        events.forEach { event ->
            if (event.id != null && event.id !in editable) throw ValidationException("Event not found in this match")
            if (event.type == EventType.ASSIST) {
                // Only an assist logged on its own (before assists rode on their goal) is sent back as one.
                if (event.id == null || editable[event.id]?.type != EventType.ASSIST) {
                    throw ValidationException("An assist is added to its goal")
                }
            } else if (event.type !in RECORD_EVENT_TYPES) {
                throw ValidationException("Substitutions can't be changed after the game")
            }
            if (event.period !in 1..match.halves) throw ValidationException("This match has ${match.halves} half(s)")
            if (event.minute !in 0..MAX_EVENT_MINUTE) throw ValidationException("The minute must be between 0 and $MAX_EVENT_MINUTE")

            if (event.isOpponent) {
                if (event.type != EventType.GOAL && event.type != EventType.OWN_GOAL) {
                    throw ValidationException("Only the opponent's goals are recorded")
                }
                if (event.playerId != null || event.assistPlayerId != null) {
                    throw ValidationException("An opponent's event has none of our players")
                }
                return@forEach
            }
            val playerId = event.playerId ?: throw ValidationException("Pick the player")
            if (playerId !in calledUp) throw ValidationException("The player wasn't called up for this match")
            val scores = event.type == EventType.GOAL || event.type == EventType.OWN_GOAL || event.type == EventType.ASSIST
            if (scores && statsKnown && playerId !in played) throw ValidationException("The player didn't play in this match")

            val assist = event.assistPlayerId ?: return@forEach
            if (event.type != EventType.GOAL) throw ValidationException("An assist can only be added to a goal by one of our players")
            if (assist == playerId) throw ValidationException("A player can't assist their own goal")
            if (assist !in calledUp) throw ValidationException("The assisting player wasn't called up for this match")
            if (statsKnown && assist !in played) throw ValidationException("The assisting player didn't play in this match")
        }
    }

    // Goals' own assists go first (they point at the goal), then removed events, then the rest.
    private suspend fun applyEvents(matchId: String, events: List<MatchRecordEvent>, existing: List<MatchEvent>) {
        val kept = events.mapNotNull { it.id }.toSet()
        existing.filter { it.type == EventType.ASSIST && it.relatedEventId != null }
            .forEach { matchEventRepository.deleteEvent(it.id) }
        existing.filter { isEditable(it) && it.id !in kept }
            .forEach { matchEventRepository.deleteEvent(it.id) }

        val byId = existing.associateBy { it.id }
        events.forEach { event ->
            val fields = { base: MatchEvent ->
                base.copy(
                    type = event.type,
                    minute = event.minute,
                    period = event.period,
                    playerId = if (event.isOpponent) null else event.playerId,
                    opponentPlayerName = if (event.isOpponent) event.opponentPlayerName else null,
                    isOpponent = event.isOpponent
                )
            }
            val goal = event.id?.let { id ->
                fields(byId.getValue(id)).also { matchEventRepository.updateEvent(it) }
            } ?: matchEventRepository.addEvent(
                fields(MatchEvent(id = Uuid.random().toString(), matchId = matchId, type = event.type, minute = event.minute, createdAt = now()))
            )
            event.assistPlayerId?.let { assist ->
                matchEventRepository.addEvent(
                    goal.copy(id = Uuid.random().toString(), type = EventType.ASSIST, playerId = assist, relatedEventId = goal.id, createdAt = now())
                )
            }
        }
    }

    // Everything but the substitutions and the assists that ride on a goal (sent as the goal's).
    private fun isEditable(event: MatchEvent): Boolean =
        event.type != EventType.SUBSTITUTION_IN && event.type != EventType.SUBSTITUTION_OUT &&
            !(event.type == EventType.ASSIST && event.relatedEventId != null)
}
