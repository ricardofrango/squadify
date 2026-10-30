package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceSetup

/** The coach's space, or null before the first setup (the server's 404). Other failures throw. */
class GetSpaceUseCase(private val spaceRepository: SpaceRepository) {
    suspend operator fun invoke(): CoachSpace? = try {
        spaceRepository.getSpace()
    } catch (e: ApiException) {
        if (e.statusCode == 404) null else throw e
    }
}

class SetUpSpaceUseCase(private val spaceRepository: SpaceRepository) {
    suspend operator fun invoke(request: SetUpSpaceRequest): SpaceSetup = spaceRepository.setUpSpace(request)
}
