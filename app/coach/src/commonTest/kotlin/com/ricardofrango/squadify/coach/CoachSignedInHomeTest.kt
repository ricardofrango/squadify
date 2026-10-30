package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.domain.GetSpaceUseCase
import com.ricardofrango.squadify.coach.domain.SpaceRepository
import com.ricardofrango.squadify.coach.presentation.navigation.CoachSignedInHome
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.navigation.Screen
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

// Where the coach app opens once signed in: the space, or its first setup before there is one.
class CoachSignedInHomeTest {
    private class FakeSpaces(private val getSpace: () -> CoachSpace) : SpaceRepository {
        override suspend fun getSpace(): CoachSpace = getSpace.invoke()
        override suspend fun setUpSpace(request: SetUpSpaceRequest): SpaceSetup = error("not used")
    }

    private val space = CoachSpace(
        space = Club(id = "s1", name = "Fontelas", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL),
        isOwner = true
    )

    private fun home(getSpace: () -> CoachSpace) = CoachSignedInHome(GetSpaceUseCase(FakeSpaces(getSpace)))

    @Test
    fun aCoachWithASpaceOpensIt() = runTest {
        assertEquals(Screen.CoachHome, home { space }.screen())
    }

    @Test
    fun beforeTheFirstSetupItOpensTheSetup() = runTest {
        assertEquals(Screen.SpaceSetup, home { throw ApiException(404, null, "Not found") }.screen())
    }

    @Test
    fun aFailedCheckOpensTheHomeWhichRetries() = runTest {
        assertEquals(Screen.CoachHome, home { throw ApiException(500, null, "Server error") }.screen())
        assertEquals(Screen.CoachHome, home { throw IllegalStateException("offline") }.screen())
    }

    @Test
    fun onlyA404MeansNoSpace() = runTest {
        assertNull(GetSpaceUseCase(FakeSpaces { throw ApiException(404, null, "Not found") })())
        assertFailsWith<ApiException> { GetSpaceUseCase(FakeSpaces { throw ApiException(500, null, "Server error") })() }
    }
}
