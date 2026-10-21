package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.UserPlayerRelationship

interface PlayerRepository {
    suspend fun getPlayersByClub(clubId: String): List<Player>
    suspend fun createPlayer(player: Player): Player
    suspend fun createPlayers(players: List<Player>): List<Player>
    suspend fun getPlayerById(id: String): Player?
    suspend fun updatePlayer(player: Player): Boolean
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
