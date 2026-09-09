package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.CreateOpponentRequest
import com.ricardofrango.footballmanager.model.Opponent

interface OpponentRepository {
    suspend fun getOpponents(clubId: String): List<Opponent>
    suspend fun createOpponent(clubId: String, request: CreateOpponentRequest): Opponent
}
