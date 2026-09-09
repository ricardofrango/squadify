package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.CreateOpponentRequest
import com.ricardofrango.clubmanager.model.Opponent

interface OpponentRepository {
    suspend fun getOpponents(clubId: String): List<Opponent>
    suspend fun createOpponent(clubId: String, request: CreateOpponentRequest): Opponent
}
