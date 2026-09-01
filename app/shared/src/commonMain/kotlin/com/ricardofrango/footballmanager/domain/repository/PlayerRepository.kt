package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Player

interface PlayerRepository {
    suspend fun getPlayers(clubId: String): List<Player>
}
