package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.Opponent

interface OpponentRepository {
    suspend fun getOpponentById(id: String): Opponent?
    suspend fun getOpponentsByClub(clubId: String): List<Opponent>
    suspend fun createOpponent(opponent: Opponent): Opponent
    suspend fun updateOpponent(opponent: Opponent): Boolean
    /** Sets (or clears, with null) only the opponent's logo path. */
    suspend fun updateLogoUrl(opponentId: String, logoUrl: String?): Boolean
    suspend fun deleteOpponent(id: String): Boolean
}
