package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.allowed
import com.sportssoftw.squadcall.domain.repository.FcmTokenRepository
import com.sportssoftw.squadcall.domain.repository.MatchEventRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.service.GoalPush
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.service.PushAudience
import com.sportssoftw.squadcall.domain.service.PushNotificationService
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.model.EventType
import com.sportssoftw.squadcall.model.MatchEvent
import com.sportssoftw.squadcall.model.MatchScore
import com.sportssoftw.squadcall.model.PushLink
import org.slf4j.LoggerFactory

enum class MatchNotificationEvent {
    SCHEDULED, RESCHEDULED, CANCELLED, KICKOFF, HALF_TIME, SECOND_HALF_STARTED, FULL_TIME, GOAL
}

/**
 * Best-effort push, to whoever the server's [PushAudience] names for the match's age group (the clubs
 * server: every member of the club; the coach server: the space's owner and that age group's staff), for one of a match's notification-worthy
 * moments: the schedule ones (a fixture created, rescheduled, or cancelled -- [SCHEDULED]/
 * [RESCHEDULED]/[CANCELLED]) and the live ones (kickoff, half-time, the second half restarting,
 * full-time, or a goal). Called by [CreateMatchUseCase], [UpdateMatchUseCase],
 * [UpdateMatchStatusUseCase], [StartMatchUseCase], and [AddMatchEventUseCase] after their own
 * transaction commits -- the same "never fail the caller" posture as
 * [com.sportssoftw.squadcall.domain.service.EmailService]/[PushNotificationService] calls elsewhere,
 * except here the data needed for the push (team/opponent names, the club's member token list) is
 * fetched in a transaction of this use case's own, since by the time it runs the triggering use
 * case's transaction has already closed.
 *
 * A goal push (given the goal itself) says whose goal it was: ours names the scorer and shows their
 * photo, theirs shows the opponent's logo, each only when there is one (see [PublicImageLinks]).
 */
class NotifyMatchEventUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val teamRepository: TeamRepository,
    private val opponentRepository: OpponentRepository,
    private val pushAudience: PushAudience,
    private val fcmTokenRepository: FcmTokenRepository,
    private val pushNotificationService: PushNotificationService,
    private val transactionManager: TransactionManager,
    private val playerRepository: PlayerRepository,
    private val publicImageLinks: PublicImageLinks,
    private val accessPolicy: AccessPolicy
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private data class NotificationContext(
        val teamName: String,
        val opponentName: String,
        val score: MatchScore,
        val tokens: List<String>,
        val link: PushLink,
        val goal: GoalPush
    )

    /**
     * [goal]: the goal (or own goal) behind a [MatchNotificationEvent.GOAL]. [causedBy]: who did it (scheduled
     * the game, scored, blew the whistle), who isn't told about it.
     */
    suspend operator fun invoke(matchId: String, event: MatchNotificationEvent, goal: MatchEvent? = null, causedBy: String? = null) {
        try {
            val context = transactionManager.runInTransaction {
                val match = matchRepository.getMatchById(matchId) ?: return@runInTransaction null
                val team = teamRepository.getTeamById(match.teamId) ?: return@runInTransaction null
                val opponent = opponentRepository.getOpponentById(match.opponentId) ?: return@runInTransaction null
                // The live moments only on a plan with following games live (AccessPolicy.requireLiveInPlan).
                if (event in LIVE_MOMENTS && !allowed { accessPolicy.requireLiveInPlan(match.clubId) }) return@runInTransaction null
                val memberIds = pushAudience.forGames(match.clubId, team.ageGroupId).filter { it != causedBy }
                val tokens = fcmTokenRepository.getTokensForUsers(memberIds).values.flatten()
                if (tokens.isEmpty()) return@runInTransaction null

                NotificationContext(
                    teamName = team.name,
                    opponentName = opponent.name,
                    score = matchEventRepository.getScoreByMatch(matchId),
                    tokens = tokens,
                    link = PushLink.match(match.clubId, matchId),
                    goal = goal?.let { g ->
                        // An own goal counts for the other side.
                        val forUs = (g.type == EventType.GOAL) != g.isOpponent
                        val scorer = g.playerId?.takeIf { forUs && g.type == EventType.GOAL && !g.isOpponent }
                            ?.let { playerRepository.getPlayerById(it) }
                        GoalPush(
                            forUs = forUs,
                            scorerName = scorer?.let { "${it.firstName} ${it.lastName}" },
                            imageUrl = if (forUs) scorer?.let { publicImageLinks.playerPhoto(it.id, it.photoUrl) }
                            else publicImageLinks.opponentLogo(opponent.id, opponent.logoUrl)
                        )
                    } ?: GoalPush()
                )
            } ?: return

            when (event) {
                MatchNotificationEvent.SCHEDULED ->
                    pushNotificationService.sendMatchScheduled(context.tokens, context.link, context.teamName, context.opponentName)
                MatchNotificationEvent.RESCHEDULED ->
                    pushNotificationService.sendMatchRescheduled(context.tokens, context.link, context.teamName, context.opponentName)
                MatchNotificationEvent.CANCELLED ->
                    pushNotificationService.sendMatchCancelled(context.tokens, context.link, context.teamName, context.opponentName)
                MatchNotificationEvent.KICKOFF ->
                    pushNotificationService.sendMatchKickedOff(context.tokens, context.link, context.teamName, context.opponentName)
                MatchNotificationEvent.HALF_TIME ->
                    pushNotificationService.sendHalfTime(context.tokens, context.link, context.teamName, context.opponentName, context.score)
                MatchNotificationEvent.SECOND_HALF_STARTED ->
                    pushNotificationService.sendSecondHalfStarted(context.tokens, context.link, context.teamName, context.opponentName)
                MatchNotificationEvent.FULL_TIME ->
                    pushNotificationService.sendMatchFinished(context.tokens, context.link, context.teamName, context.opponentName, context.score)
                MatchNotificationEvent.GOAL ->
                    pushNotificationService.sendGoalScored(context.tokens, context.link, context.teamName, context.opponentName, context.score, context.goal)
            }
        } catch (e: Exception) {
            logger.error("Match notification failed for match {} / {}", matchId, event, e)
        }
    }

    private companion object {
        val LIVE_MOMENTS = setOf(
            MatchNotificationEvent.KICKOFF, MatchNotificationEvent.HALF_TIME, MatchNotificationEvent.SECOND_HALF_STARTED,
            MatchNotificationEvent.FULL_TIME, MatchNotificationEvent.GOAL
        )
    }
}
