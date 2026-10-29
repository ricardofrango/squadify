package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.ClubAccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchPlayerRatingInput
import com.ricardofrango.squadify.model.MatchPlayerStats
import com.ricardofrango.squadify.model.MatchReport
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.SaveMatchReportRequest
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Writing the post-match report: MANAGE_MATCH gate (Coach by default), FINISHED matches only,
 * ratings 0-10 for called-up players only, and "complete" is sticky.
 */
class SaveMatchReportUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val matchReportRepository = mockk<MatchReportRepository>(relaxed = true)
    private val matchPlayerStatsRepository = mockk<MatchPlayerStatsRepository>(relaxed = true)
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = SaveMatchReportUseCase(
        matchRepository, matchReportRepository, matchPlayerStatsRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val finished = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.FINISHED)

    private fun stubRole(role: AgeGroupRole): User {
        val user = User(role.name.lowercase(), "u@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(user.id) } returns user
        coEvery { userRepository.getUserRoleInClub(user.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(user.id, ag1) } returns role
        coEvery { teamRepository.getTeamById(teamId) } returns Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true)
        return user
    }

    private fun stats(playerId: String) = MatchPlayerStats(matchId, playerId, true, 90, 0, 0, 0, 0, 0)

    @Test
    fun aCoachSavesTheReportAndRatingsAndCompletesIt() = runBlocking {
        val coach = stubRole(AgeGroupRole.COACH)
        coEvery { matchRepository.getMatchById(matchId) } returns finished
        coEvery { matchReportRepository.getByMatch(matchId) } returns null
        coEvery { matchPlayerStatsRepository.getByMatch(matchId) } returns listOf(stats("p1"), stats("p2"))
        val saved = slot<MatchReport>()
        coEvery { matchReportRepository.upsert(capture(saved)) } returns Unit

        useCase(coach.id, matchId, SaveMatchReportRequest(
            tactic = " 4-4-2 ", positiveAspects = "Pressing", teamNotes = "   ",
            opponentTactic = " 3-5-2 ",
            opponentBestPlayers = "Their #10",
            ratings = listOf(MatchPlayerRatingInput("p1", 8), MatchPlayerRatingInput("p2", null)),
            complete = true
        ))

        assertEquals("4-4-2", saved.captured.tactic)
        assertEquals("Pressing", saved.captured.positiveAspects)
        assertNull(saved.captured.teamNotes, "blank text is stored as null")
        assertEquals("Their #10", saved.captured.opponentBestPlayers)
        assertEquals("3-5-2", saved.captured.opponentTactic)
        assertNotNull(saved.captured.completedAt)
        assertEquals(coach.id, saved.captured.updatedByUserId)
        coVerify { matchPlayerStatsRepository.updateRatings(matchId, mapOf("p1" to 8, "p2" to null)) }
    }

    @Test
    fun aDraftStaysPendingButACompletedReportStaysCompleted() = runBlocking {
        val coach = stubRole(AgeGroupRole.COACH)
        coEvery { matchRepository.getMatchById(matchId) } returns finished
        val saved = slot<MatchReport>()
        coEvery { matchReportRepository.upsert(capture(saved)) } returns Unit

        coEvery { matchReportRepository.getByMatch(matchId) } returns null
        useCase(coach.id, matchId, SaveMatchReportRequest(tactic = "3-4-3", complete = false))
        assertNull(saved.captured.completedAt)

        val completedAt = Instant.fromEpochMilliseconds(5_000)
        coEvery { matchReportRepository.getByMatch(matchId) } returns
            MatchReport(matchId, completedAt = completedAt, updatedAt = completedAt, updatedByUserId = coach.id)
        useCase(coach.id, matchId, SaveMatchReportRequest(tactic = "3-4-3", complete = false))
        assertEquals(completedAt, saved.captured.completedAt)
    }

    @Test
    fun onlyAFinishedMatchCanHaveAReport() = runBlocking {
        val coach = stubRole(AgeGroupRole.COACH)
        coEvery { matchRepository.getMatchById(matchId) } returns finished.copy(status = MatchStatus.LIVE)

        assertFailsWith<ValidationException> { useCase(coach.id, matchId, SaveMatchReportRequest(tactic = "4-4-2")) }
        coVerify(exactly = 0) { matchReportRepository.upsert(any()) }
    }

    @Test
    fun ratingsMustBeBetweenZeroAndTen() = runBlocking {
        val coach = stubRole(AgeGroupRole.COACH)
        coEvery { matchRepository.getMatchById(matchId) } returns finished
        coEvery { matchPlayerStatsRepository.getByMatch(matchId) } returns listOf(stats("p1"))

        listOf(-1, 11).forEach { bad ->
            assertFailsWith<ValidationException>("rating $bad") {
                useCase(coach.id, matchId, SaveMatchReportRequest(ratings = listOf(MatchPlayerRatingInput("p1", bad))))
            }
        }
        coVerify(exactly = 0) { matchReportRepository.upsert(any()) }
    }

    @Test
    fun onlyCalledUpPlayersCanBeRatedAndOnlyOnce() = runBlocking {
        val coach = stubRole(AgeGroupRole.COACH)
        coEvery { matchRepository.getMatchById(matchId) } returns finished
        coEvery { matchPlayerStatsRepository.getByMatch(matchId) } returns listOf(stats("p1"))

        assertFailsWith<ValidationException> {
            useCase(coach.id, matchId, SaveMatchReportRequest(ratings = listOf(MatchPlayerRatingInput("not-called-up", 7))))
        }
        assertFailsWith<ValidationException> {
            useCase(coach.id, matchId, SaveMatchReportRequest(ratings = listOf(MatchPlayerRatingInput("p1", 7), MatchPlayerRatingInput("p1", 8))))
        }
        coVerify(exactly = 0) { matchReportRepository.upsert(any()) }
    }

    @Test
    fun ageGroupStaffCannotWriteItByDefault() = runBlocking {
        val staff = stubRole(AgeGroupRole.STAFF)
        coEvery { matchRepository.getMatchById(matchId) } returns finished

        assertFailsWith<ForbiddenException> { useCase(staff.id, matchId, SaveMatchReportRequest(tactic = "4-4-2")) }
        coVerify(exactly = 0) { matchReportRepository.upsert(any()) }
    }
}
