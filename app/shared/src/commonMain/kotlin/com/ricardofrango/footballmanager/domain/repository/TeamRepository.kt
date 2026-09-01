package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Team

interface TeamRepository {
    suspend fun getTeams(clubId: String): List<Team>
}
