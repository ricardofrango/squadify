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
import org.slf4j.LoggerFactory

enum class LiveMatchNotificationEvent {
    KICKOFF, HALF_TIME, SECOND_HALF_STARTED, FULL_TIME, GOAL
}

/**
 * Best-effort push, to every member of the match's club (not just its own age group -- a goal or
 * full-time whistle is club-wide news, unlike the call-up/edit permissions that stay scoped to the
 * age group -- see the developer's own note on this in the accompanying chat), for one of live
 * match's five notification-worthy moments. Called by [StartMatchUseCase], [UpdateMatchStatusUseCase],
 * and [AddMatchEventUseCase] after their own transaction commits -- the same "never fail the caller"
 * posture as [com.ricardofrango.squadify.domain.service.EmailService]/[PushNotificationService] calls
 * elsewhere, except here the data needed for the push (team/opponent names, the club's member token
 * list) is fetched in a transaction of this use case's own, since by the time it runs the triggering
 * use case's transaction has already closed.
 */
class NotifyLiveMatchEventUseCase(
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
        val tokens: List<String>
    )

    suspend operator fun invoke(matchId: String, event: LiveMatchNotificationEvent) {
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
                    tokens = tokens
                )
            } ?: return

            when (event) {
                LiveMatchNotificationEvent.KICKOFF ->
                    pushNotificationService.sendMatchKickedOff(context.tokens, context.teamName, context.opponentName)
                LiveMatchNotificationEvent.HALF_TIME ->
                    pushNotificationService.sendHalfTime(context.tokens, context.teamName, context.opponentName, context.score)
                LiveMatchNotificationEvent.SECOND_HALF_STARTED ->
                    pushNotificationService.sendSecondHalfStarted(context.tokens, context.teamName, context.opponentName)
                LiveMatchNotificationEvent.FULL_TIME ->
                    pushNotificationService.sendMatchFinished(context.tokens, context.teamName, context.opponentName, context.score)
                LiveMatchNotificationEvent.GOAL ->
                    pushNotificationService.sendGoalScored(context.tokens, context.teamName, context.opponentName, context.score)
            }
        } catch (e: Exception) {
            logger.error("Live match notification failed for match {} / {}", matchId, event, e)
        }
    }
}
