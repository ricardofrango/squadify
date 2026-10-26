package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.UserPlayerRelationship

/**
 * Which of the club's players a page of the Players screen holds (GetPlayersUseCase.page). Every set
 * filter applies; null ones don't.
 */
data class PlayerListFilter(
    // Only players in these age groups' pools: what a viewer below coordinator may see.
    val visibleAgeGroupIds: Set<String>? = null,
    // A season, as its age groups: players in one of their pools, plus players in no pool yet (just
    // registered to the club), so registering a player never looks like it did nothing.
    val seasonAgeGroupIds: Set<String>? = null,
    val ageGroupId: String? = null,
    // Already folded with SearchText.key; found anywhere in "first last".
    val search: String? = null,
    // Adding to an age group: players not in this pool yet, active, born in [bornFromYear] or later
    // (old enough for it).
    val notInAgeGroupId: String? = null,
    val bornFromYear: Int? = null,
    val activeOnly: Boolean = false
)

interface PlayerRepository {
    suspend fun getPlayersByClub(clubId: String): List<Player>
    // One page of the club's players matching [filter]: active ones first, then by name.
    suspend fun getPlayersPage(clubId: String, filter: PlayerListFilter, limit: Int, offset: Int): List<Player>
    // Whether the club has any player matching [filter] at all.
    suspend fun hasPlayers(clubId: String, filter: PlayerListFilter): Boolean
    // How many players each age group's pool holds, by age group id (pools with none are left out).
    suspend fun countPlayersByAgeGroup(clubId: String): Map<String, Int>
    suspend fun createPlayer(player: Player): Player
    suspend fun createPlayers(players: List<Player>): List<Player>
    suspend fun getPlayerById(id: String): Player?
    suspend fun updatePlayer(player: Player): Boolean
    /** Sets (or clears, with null) only the player's photo path. */
    suspend fun updatePhotoUrl(playerId: String, photoUrl: String?): Boolean
    suspend fun getPlayersData(playerIds: List<String>): List<Player>
    suspend fun addPlayersToAgeGroup(links: List<AgeGroupPlayer>): List<AgeGroupPlayer>
    suspend fun getPlayersByAgeGroup(ageGroupId: String): List<Player>
    suspend fun getAgeGroupPlayersByClub(clubId: String): List<AgeGroupPlayer>
    suspend fun updateActiveStatus(playerId: String, active: Boolean): Boolean
    // Takes the player out of one age group's pool (only the link; the player and their match history stay).
    suspend fun removePlayerFromAgeGroup(ageGroupId: String, playerId: String): Boolean
    suspend fun createUserPlayer(userId: String, playerId: String, relationship: UserPlayerRelationship)
    suspend fun createUserPlayers(userPlayers: List<Triple<String, String, UserPlayerRelationship>>)
    suspend fun deleteUserPlayersByPlayer(playerId: String)
    suspend fun countPlayersByClub(clubId: String): Long
    suspend fun getPlayersByUser(userId: String): List<Player>
    // Every user -> player link of the club's players: (userId, player, relationship).
    suspend fun getUserPlayerLinksByClub(clubId: String): List<Triple<String, Player, UserPlayerRelationship>>
    suspend fun countGuardiansByPlayer(playerId: String): Long
    suspend fun getAgeGroupIdsByPlayer(playerId: String): List<String>
}
