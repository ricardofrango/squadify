package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.TeamPlayer

interface PlayerRepository {
    suspend fun getPlayersByClub(clubId: String): List<Player>
    suspend fun createPlayer(player: Player): Boolean
    suspend fun getPlayerById(id: String): Player?
    suspend fun addPlayerToTeam(teamPlayer: TeamPlayer): Boolean
    suspend fun getPlayersByTeam(teamId: String): List<Player>
}
