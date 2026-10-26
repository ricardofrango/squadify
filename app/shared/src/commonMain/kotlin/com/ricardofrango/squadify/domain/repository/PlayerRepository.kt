package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.Paging
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayersPage
import com.ricardofrango.squadify.model.PlayerProfile
import com.ricardofrango.squadify.model.PlayerSeasonStats
import com.ricardofrango.squadify.model.UpdatePlayerRequest

interface PlayerRepository {
    // One page of the club's players: active first, then by name; the filters run on the server.
    suspend fun getPlayersPage(
        clubId: String,
        seasonId: String?,
        ageGroupId: String?,
        query: String,
        offset: Int,
        limit: Int = Paging.PAGE_SIZE
    ): PlayersPage
    suspend fun getPlayerById(playerId: String): PlayerProfile
    suspend fun getPlayerSeasonStats(playerId: String): PlayerSeasonStats
    suspend fun createPlayers(request: CreatePlayersRequest): List<Player>
    suspend fun updatePlayer(playerId: String, request: UpdatePlayerRequest)
    /** Uploads [bytes] (PNG or JPEG) as the player's photo; returns the new photo path. */
    suspend fun uploadPlayerPhoto(playerId: String, bytes: ByteArray): String
    suspend fun deletePlayerPhoto(playerId: String)
    // Disabled in the club: still in their age groups, out of upcoming call-ups; the data stays.
    suspend fun disablePlayer(playerId: String)
    // Active again.
    suspend fun reactivatePlayer(playerId: String)
}
