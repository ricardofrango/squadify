package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.ClubAccessPolicy
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadify.domain.repository.MatchEventRepository
import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.domain.repository.UserRepository
import com.sportssoftw.squadify.domain.service.LiveMatchBus
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.ForbiddenException
import com.sportssoftw.squadify.exception.ValidationException
import com.sportssoftw.squadify.model.AgeGroupRole
import com.sportssoftw.squadify.model.ClubRole
import com.sportssoftw.squadify.model.LiveMatchUpdate
import com.sportssoftw.squadify.model.Match
import com.sportssoftw.squadify.model.MatchScore
import com.sportssoftw.squadify.model.MatchStatus
import com.sportssoftw.squadify.model.Team
import com.sportssoftw.squadify.model.User
import com.sportssoftw.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class TakeOverLiveMatchUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val matchEventRepository = mockk<MatchEventRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val liveMatchBus = mockk<LiveMatchBus>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = TakeOverLiveMatchUseCase(
        matchRepository, matchEventRepository, liveMatchBus, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"

    private fun user(id: String) = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    private fun stub(user: User, ageGroupRole: AgeGroupRole?) {
        coEvery { userRepository.findById(user.id) } returns user
        coEvery { userRepository.getUserRoleInClub(user.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(user.id, ag1) } returns ageGroupRole
        coEvery { teamRepository.getTeamById(teamId) } returns Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true)
    }

    private fun match(status: MatchStatus) =
        Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = status, liveManagerUserId = "kicked-off-by")

    @Test
    fun anotherStaffMemberTakesTheGameOverAndEveryPhoneHearsIt() = runBlocking {
        val staff = user("staff")
        stub(staff, AgeGroupRole.STAFF)
        coEvery { matchRepository.getMatchById(matchId) } returns match(MatchStatus.HALF_TIME)
        coEvery { matchRepository.setLiveManager(matchId, staff.id) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 1, 0)
        val update = slot<LiveMatchUpdate>()
        coEvery { liveMatchBus.publish(matchId, capture(update)) } returns Unit

        useCase(staff.id, matchId)

        coVerify { matchRepository.setLiveManager(matchId, staff.id) }
        assertEquals(staff.id, update.captured.match?.liveManagerUserId)
    }

    @Test
    fun someoneOutsideTheAgeGroupCantTakeItOver() = runBlocking {
        val member = user("member")
        stub(member, null)
        coEvery { matchRepository.getMatchById(matchId) } returns match(MatchStatus.LIVE)

        assertFailsWith<ForbiddenException> { useCase(member.id, matchId) }
        coVerify(exactly = 0) { matchRepository.setLiveManager(any(), any()) }
    }

    @Test
    fun onlyWhileTheGameIsOn() = runBlocking {
        val staff = user("staff")
        stub(staff, AgeGroupRole.STAFF)
        coEvery { matchRepository.getMatchById(matchId) } returns match(MatchStatus.FINISHED)

        assertFailsWith<ValidationException> { useCase(staff.id, matchId) }
        coVerify(exactly = 0) { matchRepository.setLiveManager(any(), any()) }
    }
}
