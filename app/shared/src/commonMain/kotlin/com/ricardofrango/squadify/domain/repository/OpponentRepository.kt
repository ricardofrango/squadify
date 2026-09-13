package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.Opponent

interface OpponentRepository {
    suspend fun getOpponents(clubId: String): List<Opponent>
    suspend fun createOpponent(clubId: String, request: CreateOpponentRequest): Opponent
}
