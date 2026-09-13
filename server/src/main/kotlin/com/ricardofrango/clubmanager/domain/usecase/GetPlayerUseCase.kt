package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.MatchEventRepository
import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.domain.repository.OpponentRepository
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.repository.SeasonRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ForbiddenException
import com.ricardofrango.clubmanager.exception.NotFoundException
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.EventType
import com.ricardofrango.clubmanager.model.PlayerProfile
import com.ricardofrango.clubmanager.model.PlayerProfileEvent

/** How many of a player's most recent events to enrich with match context and return. */
private const val RECENT_EVENTS_LIMIT = 5

/**
 * The aggregate behind the player profile screen: the player record plus derived stats (goals,
 * assists, times called up -- all-time, there's no season/career split yet) and their most recent
 * logged match events, each resolved to an opponent name and date. Readable by any club member.
 */
class GetPlayerUseCase(
    private val playerRepository: PlayerRepository,
    private val matchEventRepository: MatchEventRepository,
    private val matchRepository: MatchRepository,
    private val opponentRepository: OpponentRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, playerId: String): PlayerProfile = transactionManager.runInTransaction {
        val player = playerRepository.getPlayerById(playerId) ?: throw NotFoundException("Player not found")

        checkUserPermissionUseCase(userId, player.clubId, minClubRole = ClubRole.MEMBER)

        val ageGroup = playerRepository.getAgeGroupIdsByPlayer(playerId)
            .firstNotNullOfOrNull { ageGroupRepository.getAgeGroupById(it) }
        val ageGroupLabel = ageGroup?.name
        val seasonLabel = ageGroup?.let { seasonRepository.getSeasonById(it.seasonId) }?.name

        val canManage = try {
            checkUserPermissionUseCase(userId, player.clubId, minClubRole = ClubRole.COORDINATOR)
            true
        } catch (e: ForbiddenException) {
            false
        }

        // Newest first (see getAllEventsByPlayer) -- counted for the stat totals, and its head doubles
        // as the "recent events" list once each is resolved to its match/opponent.
        val events = matchEventRepository.getAllEventsByPlayer(playerId)
        val goals = events.count { it.type == EventType.GOAL }
        val assists = events.count { it.type == EventType.ASSIST }

        val recentEvents = events.take(RECENT_EVENTS_LIMIT).mapNotNull { event ->
            val match = matchRepository.getMatchById(event.matchId) ?: return@mapNotNull null
            val opponent = opponentRepository.getOpponentById(match.opponentId) ?: return@mapNotNull null
            PlayerProfileEvent(
                type = event.type,
                minute = event.minute,
                opponentName = opponent.shortName ?: opponent.name,
                matchDate = match.dateTime
            )
        }

        PlayerProfile(
            player = player,
            ageGroupLabel = ageGroupLabel,
            seasonLabel = seasonLabel,
            goals = goals,
            assists = assists,
            timesCalledUp = matchRepository.countCallUpsByPlayer(playerId).toInt(),
            guardiansLinked = playerRepository.countGuardiansByPlayer(playerId).toInt(),
            recentEvents = recentEvents,
            canManage = canManage
        )
    }
}
