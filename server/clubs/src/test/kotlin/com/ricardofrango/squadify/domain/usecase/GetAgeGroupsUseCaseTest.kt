package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.ClubAccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import org.junit.Test
import kotlin.test.assertEquals

/**
 * A non-admin is scoped to the age group(s) they're attached to directly -- an `AgeGroupStaff` role
 * or a linked player in that age group's pool. Empty when they have none.
 */
class GetAgeGroupsUseCaseTest {

    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val userRepository = mockk<UserRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = GetAgeGroupsUseCase(
        ageGroupRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager
    )

    private val clubIdA = "club-a"

    private fun ageGroup(id: String) = AgeGroup(
        id = id, clubId = clubIdA, name = id, seasonId = "season-1", startYear = 2016, endYear = 2016
    )

    private val agU10 = ageGroup("age-group-u10")
    private val agU12 = ageGroup("age-group-u12")
    private val agU14 = ageGroup("age-group-u14")

    private fun user(id: String) = User(
        id = id, email = "$id@test.com", firstName = "Test", lastName = "User",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun child(id: String) = Player(
        id = id, clubId = clubIdA, firstName = "Test", lastName = "Child",
        birthDate = LocalDate(2012, 1, 1), active = true
    )

    @Test
    fun clubAdminSeesEveryAgeGroupInTheClub() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        val every = listOf(agU10, agU12)
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns every

        assertEquals(every, useCase(admin.id, clubIdA))
        Unit
    }

    @Test
    fun plainMemberWithNoAgeGroupSeesNone() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(agU10, agU12, agU14)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, any()) } returns null

        assertEquals(emptyList(), useCase(member.id, clubIdA))
        Unit
    }

    @Test
    fun coachSeesOnlyTheAgeGroupsTheyStaff() = runBlocking {
        val coach = user("p7-coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { playerRepository.getPlayersByUser(coach.id) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(agU10, agU12, agU14)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, agU10.id) } returns AgeGroupRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, agU14.id) } returns AgeGroupRole.STAFF
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, agU12.id) } returns null

        assertEquals(setOf(agU10.id, agU14.id), useCase(coach.id, clubIdA).map { it.id }.toSet())
        Unit
    }

    @Test
    fun parentSeesOnlyTheirChildsAgeGroup() = runBlocking {
        val parent = user("p10-parent")
        val kid = child("child-1")
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(kid)
        coEvery { playerRepository.getAgeGroupPlayersByClub(clubIdA) } returns listOf(
            AgeGroupPlayer(id = "agp-1", ageGroupId = agU12.id, playerId = kid.id)
        )
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(agU10, agU12, agU14)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, any()) } returns null

        assertEquals(listOf(agU12), useCase(parent.id, clubIdA))
        Unit
    }
}
