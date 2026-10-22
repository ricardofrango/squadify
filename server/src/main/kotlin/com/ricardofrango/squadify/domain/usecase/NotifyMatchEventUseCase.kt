package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.PushLink
import org.slf4j.LoggerFactory

enum class MatchNotificationEvent {
    SCHEDULED, RESCHEDULED, CANCELLED, KICKOFF, HALF_TIME, SECOND_HALF_STARTED, FULL_TIME, GOAL
}

/**
 * Best-effort push, to every member of the match's club (not just its own age group -- see the
 * developer's own note on this in the accompanying chat), for one of a match's notification-worthy
 * moments: the schedule ones (a fixture created, rescheduled, or cancelled -- [SCHEDULED]/
 * [RESCHEDULED]/[CANCELLED]) and the live ones (kickoff, half-time, the second half restarting,
 * full-time, or a goal). Called by [CreateMatchUseCase], [UpdateMatchUseCase],
 * [UpdateMatchStatusUseCase], [StartMatchUseCase], and [AddMatchEventUseCase] after their own
 * transaction commits -- the same "never fail the caller" posture as
 * [com.ricardofrango.squadify.domain.service.EmailService]/[PushNotificationService] calls elsewhere,
 * except here the data needed for the push (team/opponent names, the club's member token list) is
 * fetched in a transaction of this use case's own, since by the time it runs the triggering use
 * case's transaction has already closed.
 */
class NotifyMatchEventUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val teamRepository: TeamRepository,
    private val opponentRepository: OpponentRepository,
    private val clubRepository: ClubRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val pushNotificationService: PushNotificationService,
    private val transactionManager: TransactionManager
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private data class NotificationContext(
        val teamName: String,
        val opponentName: String,
        val score: MatchScore,
        val tokens: List<String>,
        val link: PushLink
    )

    suspend operator fun invoke(matchId: String, event: MatchNotificationEvent) {
        try {
            val context = transactionManager.runInTransaction {
                val match = matchRepository.getMatchById(matchId) ?: return@runInTransaction null
                val team = teamRepository.getTeamById(match.teamId) ?: return@runInTransaction null
                val opponent = opponentRepository.getOpponentById(match.opponentId) ?: return@runInTransaction null
                val memberIds = clubRepository.getClubMembers(match.clubId).map { it.userId }
                val tokens = fcmTokenRepository.getTokensForUsers(memberIds).values.flatten()
                if (tokens.isEmpty()) return@runInTransaction null

                NotificationContext(
                    teamName = team.name,
                    opponentName = opponent.name,
                    score = matchEventRepository.getScoreByMatch(matchId),
                    tokens = tokens,
                    link = PushLink.match(match.clubId, matchId)
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
                    pushNotificationService.sendGoalScored(context.tokens, context.link, context.teamName, context.opponentName, context.score)
            }
        } catch (e: Exception) {
            logger.error("Match notification failed for match {} / {}", matchId, event, e)
        }
    }
}
