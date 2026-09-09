package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.Opponent

interface OpponentRepository {
    suspend fun getOpponentById(id: String): Opponent?
    suspend fun getOpponentsByClub(clubId: String): List<Opponent>
    suspend fun createOpponent(opponent: Opponent): Opponent
    suspend fun updateOpponent(opponent: Opponent): Boolean
    suspend fun deleteOpponent(id: String): Boolean
}
