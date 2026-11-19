package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.AccessPolicy
import com.sportssoftw.squadify.domain.access.GameScope
import com.sportssoftw.squadify.domain.access.allowed
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.MatchEventRepository
import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.domain.repository.OpponentRepository
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.domain.repository.SeasonRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.model.isTally
import com.sportssoftw.squadify.model.EventType
import com.sportssoftw.squadify.model.PlayerProfile
import com.sportssoftw.squadify.model.PlayerProfileEvent

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
    private val accessPolicy: AccessPolicy,
    private val playerContacts: PlayerContacts,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, playerId: String): PlayerProfile = transactionManager.runInTransaction {
        val player = playerRepository.getPlayerById(playerId) ?: throw NotFoundException("Player not found")

        accessPolicy.requireMember(userId, player.clubId)

        val ageGroup = playerRepository.getAgeGroupIdsByPlayer(playerId)
            .firstNotNullOfOrNull { ageGroupRepository.getAgeGroupById(it) }
        val ageGroupLabel = ageGroup?.name
        val seasonLabel = ageGroup?.let { seasonRepository.getSeasonById(it.seasonId) }?.name

        val canEditPlayer = allowed { accessPolicy.requireEditPlayer(userId, GameScope(player.clubId, ageGroupId = ageGroup?.id)) }

        // Newest first (see getAllEventsByPlayer) -- counted for the stat totals, and its head doubles
        // as the "recent events" list once each is resolved to its match/opponent.
        val events = matchEventRepository.getAllEventsByPlayer(playerId)
        val goals = events.count { it.type == EventType.GOAL }
        val assists = events.count { it.type == EventType.ASSIST }

        // Goals, assists and cards; not the live game's shots, fouls and corners.
        val recentEvents = events.filterNot { it.type.isTally() }.take(RECENT_EVENTS_LIMIT).mapNotNull { event ->
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
            ageGroupId = ageGroup?.id,
            ageGroupLabel = ageGroupLabel,
            seasonLabel = seasonLabel,
            goals = goals,
            assists = assists,
            timesCalledUp = matchRepository.countCallUpsByPlayer(playerId).toInt(),
            guardiansLinked = playerContacts.guardiansLinked(playerId),
            recentEvents = recentEvents,
            canEditPlayer = canEditPlayer
        )
    }
}
