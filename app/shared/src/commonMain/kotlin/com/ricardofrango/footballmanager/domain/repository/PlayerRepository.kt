package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.CreatePlayersRequest
import com.ricardofrango.footballmanager.model.Player

interface PlayerRepository {
    suspend fun getPlayers(clubId: String): List<Player>
    suspend fun getPlayerById(playerId: String): Player
    suspend fun createPlayers(request: CreatePlayersRequest): List<Player>
}
