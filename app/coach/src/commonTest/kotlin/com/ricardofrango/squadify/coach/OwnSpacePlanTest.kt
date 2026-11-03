package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
import com.ricardofrango.squadify.coach.domain.OwnSpacePlan
import com.ricardofrango.squadify.coach.domain.SpaceRepository
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.domain.config.OwnSpace
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

// The own space behind the plan messages (REQUIREMENTS §5c): the coach's own space's plan and age groups
// this season, whatever plan the spaces they're staff in have.
class OwnSpacePlanTest {
    private fun space(id: String) =
        Club(id = id, name = id, status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)

    private class Spaces(var spaces: List<SpaceAgeGroups>) : SpaceRepository {
        override suspend fun getSpaces() = spaces
        override suspend fun setUpSpace(request: SetUpSpaceRequest): SpaceSetup = error("not used")
        override suspend fun acceptInvite(code: String): CoachSpace = error("not used")
    }

    @Test
    fun eachLoadKeepsTheOwnSpace() = runTest {
        val ownPlan = OwnSpacePlan()
        val repository = Spaces(
            listOf(
                SpaceAgeGroups(space("mine"), isOwner = true, ageGroups = emptyList(), plan = SpacePlan.BASIC, ageGroupsThisSeason = 2),
                SpaceAgeGroups(space("anas"), isOwner = false, ageGroups = emptyList(), plan = SpacePlan.FREE)
            )
        )
        val getSpaces = GetSpacesUseCase(repository, ownPlan)
        assertNull(ownPlan.space.value)

        getSpaces()
        assertEquals(OwnSpace("mine", SpacePlan.BASIC, ageGroupsThisSeason = 2), ownPlan.space.value)

        // Staff only: no own plan, so See plans opens Plans with none marked as theirs.
        repository.spaces = listOf(SpaceAgeGroups(space("anas"), isOwner = false, ageGroups = emptyList(), plan = SpacePlan.FREE))
        getSpaces()
        assertNull(ownPlan.space.value)
    }
}
