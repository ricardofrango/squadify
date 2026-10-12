package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.OpponentDetails
import com.ricardofrango.squadify.model.OpponentList

interface OpponentRepository {
    // ageGroupId: pass the age group this call is happening in (e.g. picking an opponent from the
    // create-match screen) so a coach of that specific age group -- not just a club admin/coordinator
    // -- is authorized server-side. Null for contexts with no age-group scope. A GET has no body, so
    // this travels as a query param; createOpponent's equivalent lives on CreateOpponentRequest itself.
    suspend fun getOpponents(clubId: String, ageGroupId: String? = null): List<Opponent>
    // The Opponents screen: the list, its Recent section and whether the caller can create one.
    suspend fun getOpponentList(clubId: String, ageGroupId: String? = null): OpponentList
    suspend fun getOpponentDetails(opponentId: String): OpponentDetails
    suspend fun createOpponent(clubId: String, request: CreateOpponentRequest): Opponent
}
