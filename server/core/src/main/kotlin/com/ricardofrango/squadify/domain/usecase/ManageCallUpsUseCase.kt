package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.scope
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.PushAudience
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.*
import org.slf4j.LoggerFactory

/**
 * Saves a match's call-up. When it's saved ready (at least the match's size) and changed, the age
 * group's call-up audience ([PushAudience.forCallUps]) is told, except whoever saved it (the developer,
 * 2026-10-31). Sent after the transaction commits, best effort.
 */
class ManageCallUpsUseCase(
    private val matchRepository: MatchRepository,
    private val transactionManager: TransactionManager,
    private val accessPolicy: AccessPolicy,
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val opponentRepository: OpponentRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val pushNotificationService: PushNotificationService,
    private val pushAudience: PushAudience
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private class ReadyPush(val tokens: List<String>, val link: PushLink, val teamName: String, val opponentName: String, val players: Int)

    suspend operator fun invoke(
        userId: String,
        matchId: String,
        request: ManageCallUpRequest
    ): Boolean {
        var push: ReadyPush? = null
        val saved = transactionManager.runInTransaction {
            // 1. Fetch match first to get context (required for granular check)
            val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

            // 2. Single permission check with full context from match
            accessPolicy.requireManageCallUps(userId, match.scope, "Insufficient permissions to manage call-ups")

            // A disabled player can't be newly called up (one already in the list can stay, e.g. a played match).
            val alreadyIn = matchRepository.getCallUpsForMatch(matchId).map { it.player.id }.toSet()
            val added = request.playerIds.filter { it !in alreadyIn }
            if (added.isNotEmpty()) {
                playerRepository.getPlayersData(added).firstOrNull { !it.active }?.let {
                    throw ValidationException("${it.firstName} ${it.lastName} is disabled and can't be called up")
                }
            }

            val saved = matchRepository.manageCallUps(matchId, request.playerIds)
            val players = request.playerIds.toSet()
            if (saved && match.status == MatchStatus.SCHEDULED && players.size >= match.teamSize && players != alreadyIn) {
                push = readyPush(match, players.size, userId)
            }
            saved
        }
        push?.let {
            try {
                pushNotificationService.sendCallUpReady(it.tokens, it.link, it.teamName, it.opponentName, it.players)
            } catch (e: Exception) {
                logger.error("Call-up ready push failed for match {}", matchId, e)
            }
        }
        return saved
    }

    // Never fails the save: a lookup that goes wrong only means no push.
    private suspend fun readyPush(match: Match, players: Int, savedBy: String): ReadyPush? = try {
        readyPushOrNull(match, players, savedBy)
    } catch (e: Exception) {
        logger.error("Couldn't prepare the call-up ready push for match {}", match.id, e)
        null
    }

    private suspend fun readyPushOrNull(match: Match, players: Int, savedBy: String): ReadyPush? {
        val team = teamRepository.getTeamById(match.teamId) ?: return null
        val opponent = opponentRepository.getOpponentById(match.opponentId) ?: return null
        val audience = pushAudience.forCallUps(match.clubId, team.ageGroupId).filter { it != savedBy }
        val tokens = fcmTokenRepository.getTokensForUsers(audience).values.flatten()
        if (tokens.isEmpty()) return null
        return ReadyPush(tokens, PushLink.match(match.clubId, match.id), team.name, opponent.name, players)
    }
}
