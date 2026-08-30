package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.TeamPlayer
import com.ricardofrango.footballmanager.model.UserPlayerRelationship

interface PlayerRepository {
    suspend fun getPlayersByClub(clubId: String): List<Player>
    suspend fun createPlayer(player: Player): Player
    suspend fun createPlayers(players: List<Player>): List<Player>
    suspend fun getPlayerById(id: String): Player?
    suspend fun updatePlayer(player: Player): Boolean
    suspend fun getPlayersData(playerIds: List<String>): List<Player>
    suspend fun addPlayerToTeam(teamPlayer: TeamPlayer): TeamPlayer
    suspend fun addPlayersToTeam(teamPlayer: List<TeamPlayer>): List<TeamPlayer>
    suspend fun getPlayersByTeam(teamId: String): List<Player>
    suspend fun updateActiveStatus(playerId: String, active: Boolean): Boolean
    suspend fun createUserPlayer(userId: String, playerId: String, relationship: UserPlayerRelationship)
    suspend fun createUserPlayers(userPlayers: List<Triple<String, String, UserPlayerRelationship>>)
    suspend fun deleteUserPlayersByPlayer(playerId: String)
}
