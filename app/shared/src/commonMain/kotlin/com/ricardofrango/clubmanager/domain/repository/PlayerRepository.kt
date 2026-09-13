package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.CreatePlayersRequest
import com.ricardofrango.clubmanager.model.Player
import com.ricardofrango.clubmanager.model.PlayerProfile
import com.ricardofrango.clubmanager.model.UpdatePlayerRequest

interface PlayerRepository {
    suspend fun getPlayers(clubId: String): List<Player>
    suspend fun getPlayerById(playerId: String): PlayerProfile
    suspend fun createPlayers(request: CreatePlayersRequest): List<Player>
    suspend fun updatePlayer(playerId: String, request: UpdatePlayerRequest)
}
