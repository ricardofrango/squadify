package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.Opponent

interface OpponentRepository {
    // ageGroupId: pass the age group this call is happening in (e.g. picking an opponent from the
    // create-match screen) so a coach of that specific age group -- not just a club admin/coordinator
    // -- is authorized server-side. Null for contexts with no age-group scope. A GET has no body, so
    // this travels as a query param; createOpponent's equivalent lives on CreateOpponentRequest itself.
    suspend fun getOpponents(clubId: String, ageGroupId: String? = null): List<Opponent>
    suspend fun createOpponent(clubId: String, request: CreateOpponentRequest): Opponent
}
