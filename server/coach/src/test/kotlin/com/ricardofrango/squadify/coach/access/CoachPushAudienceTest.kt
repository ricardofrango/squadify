package com.ricardofrango.squadify.coach.access

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.AgeGroupStaffMember
import com.ricardofrango.squadify.model.ClubMemberSummary
import com.ricardofrango.squadify.model.ClubRole
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

// Who gets an age group's notifications on the coach server: the space's owner and that age group's
// staff, never staff on the space's other age groups (the developer, 2026-10-31).
class CoachPushAudienceTest {
    private val clubRepository = mockk<ClubRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val audience = CoachPushAudience(clubRepository, ageGroupRepository)

    @Test
    fun theOwnerAndTheAgeGroupsStaff() = runBlocking {
        coEvery { clubRepository.getClubMembers("s1") } returns listOf(
            ClubMemberSummary("owner", "A", "O", "o@t.com", ClubRole.CLUB_ADMIN),
            ClubMemberSummary("sub12-staff", "B", "S", "b@t.com", ClubRole.MEMBER),
            ClubMemberSummary("sub14-staff", "C", "S", "c@t.com", ClubRole.MEMBER)
        )
        coEvery { ageGroupRepository.getAgeGroupStaff("sub12") } returns listOf(AgeGroupStaffMember("sub12-staff", "B", "S", AgeGroupRole.COACH))

        assertEquals(listOf("owner", "sub12-staff"), audience.forGames("s1", "sub12"))
        assertEquals(listOf("owner", "sub12-staff"), audience.forCallUps("s1", "sub12"))
    }
}
