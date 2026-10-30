package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceSetup

/** The coach's space on the coach server (`/v1/space`, TECHNICAL_DECISIONS §72). */
interface SpaceRepository {
    /** The signed-in coach's space; an ApiException with 404 before the first setup. */
    suspend fun getSpace(): CoachSpace

    /** The first setup: the space, its first age group and team. 409 once the account has a space. */
    suspend fun setUpSpace(request: SetUpSpaceRequest): SpaceSetup
}
