package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerProfile
import com.ricardofrango.squadify.model.PlayerSeasonStats
import com.ricardofrango.squadify.model.UpdatePlayerRequest

interface PlayerRepository {
    suspend fun getPlayers(clubId: String): List<Player>
    suspend fun getPlayerById(playerId: String): PlayerProfile
    suspend fun getPlayerSeasonStats(playerId: String): PlayerSeasonStats
    suspend fun createPlayers(request: CreatePlayersRequest): List<Player>
    suspend fun updatePlayer(playerId: String, request: UpdatePlayerRequest)
    // Disabled in the club: still in their age groups, out of upcoming call-ups; the data stays.
    suspend fun disablePlayer(playerId: String)
    // Active again.
    suspend fun reactivatePlayer(playerId: String)
}
