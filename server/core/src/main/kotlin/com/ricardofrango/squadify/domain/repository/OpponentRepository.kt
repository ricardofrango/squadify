package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.Opponent

interface OpponentRepository {
    suspend fun getOpponentById(id: String): Opponent?
    suspend fun getOpponentsByClub(clubId: String): List<Opponent>
    /**
     * One page of the club's opponents by name (lowercase, accents folded), whose name or short name
     * contains [search] (already folded with `SearchText.key`; null or empty for all).
     */
    suspend fun getOpponentsPage(clubId: String, search: String?, limit: Int, offset: Int): List<Opponent>
    suspend fun hasOpponents(clubId: String): Boolean
    suspend fun createOpponent(opponent: Opponent): Opponent
    suspend fun updateOpponent(opponent: Opponent): Boolean
    /** Sets (or clears, with null) only the opponent's logo path. */
    suspend fun updateLogoUrl(opponentId: String, logoUrl: String?): Boolean
    suspend fun deleteOpponent(id: String): Boolean
}
