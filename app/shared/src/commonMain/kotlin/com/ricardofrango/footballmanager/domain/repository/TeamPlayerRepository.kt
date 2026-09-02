package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.TeamPlayer

interface TeamPlayerRepository {
    suspend fun getTeamPlayersByClub(clubId: String): List<TeamPlayer>
}
