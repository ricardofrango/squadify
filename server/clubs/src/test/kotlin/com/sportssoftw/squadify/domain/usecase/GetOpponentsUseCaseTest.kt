package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.ClubAccessPolicy
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.OpponentRepository
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.domain.repository.UserRepository
import com.sportssoftw.squadify.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.ForbiddenException
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.model.AgeGroup
import com.sportssoftw.squadify.model.ClubRole
import com.sportssoftw.squadify.model.Opponent
import com.sportssoftw.squadify.model.AgeGroupRole
import com.sportssoftw.squadify.model.User
import com.sportssoftw.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class GetOpponentsUseCaseTest {

    private val opponentRepository = mockk<OpponentRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = GetOpponentsUseCase(opponentRepository, ageGroupRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager)

    private val clubId = "club-1"
    private val ag1 = "ag-1"
    private val opponents = listOf(Opponent("o1", clubId, "Rivals FC"))

    private fun coach() = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    @Test
    fun anAgeGroupCoachWithNoClubRoleCanListOpponentsForTheirOwnAgeGroup() = runBlocking {
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, clubId, "U12", "s-1", 2013, 2013)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { opponentRepository.getOpponentsByClub(clubId) } returns opponents

        val result = useCase(coach.id, clubId, ageGroupId = ag1)

        assertEquals(opponents, result)
    }

    @Test
    fun aPlainClubMemberCanListOpponentsWithNoAgeGroupContext() = runBlocking {
        val member = User("p4-member", "m@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER
        coEvery { opponentRepository.getOpponentsByClub(clubId) } returns opponents

        val result = useCase(member.id, clubId)

        assertEquals(opponents, result)
    }

    @Test
    fun someoneWithNoClubRoleAndNoAgeGroupContextIsDenied() = runBlocking {
        val outsider = User("p12-outsider", "o@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubId) } returns null

        assertFailsWith<ForbiddenException> { useCase(outsider.id, clubId) }
        Unit
    }

    @Test
    fun anAgeGroupFromAnotherClubIsRejected() = runBlocking {
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, "other-club", "U12", "s-1", 2013, 2013)

        assertFailsWith<ForbiddenException> { useCase(coach.id, clubId, ageGroupId = ag1) }
        Unit
    }

    @Test
    fun aNonexistentAgeGroupIsNotFound() = runBlocking {
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { ageGroupRepository.getAgeGroupById("no-such-ag") } returns null

        assertFailsWith<NotFoundException> { useCase(coach.id, clubId, ageGroupId = "no-such-ag") }
        Unit
    }
}
