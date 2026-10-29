package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.ClubAccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchReportStatus
import com.ricardofrango.squadify.model.MatchPlayerStats
import com.ricardofrango.squadify.model.MatchReport
import com.ricardofrango.squadify.model.MatchLineupChange
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class GetMatchDetailsUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val opponentRepository = mockk<OpponentRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val matchEventRepository = mockk<MatchEventRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val matchReportRepository = mockk<MatchReportRepository>()
    private val matchPlayerStatsRepository = mockk<MatchPlayerStatsRepository>()
    private val clubRepository = mockk<ClubRepository>()
    private val useCase = GetMatchDetailsUseCase(
        matchRepository, opponentRepository, teamRepository, ageGroupRepository, seasonRepository,
        playerRepository, matchEventRepository, matchReportRepository, matchPlayerStatsRepository, userRepository,
        ClubAccessPolicy(checkUserPermissionUseCase), transactionManager, clubRepository
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"

    private val match = Match(matchId, clubId, teamId, "opp-1", "Field", Instant.fromEpochMilliseconds(1), notes = "n")
    private val team = Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
    private val ageGroup = AgeGroup(ag1, clubId, "Sub-15", "s-1", 2011, 2011)
    private val season = Season("s-1", clubId, "26/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 7, 1), LocalDate(2027, 6, 30), true)
    private val opponent = Opponent("opp-1", clubId, "AD Fontelas", "AD Fontelas", null)

    private fun user(id: String) = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
    private fun player(id: String) = Player(id, clubId, id, "P", LocalDate(2012, 1, 1), null, null, true)

    private fun baseStubs() {
        coEvery { clubRepository.getClubById(clubId) } returns Club(
            clubId, "CD Ribamar", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL,
            logoUrl = "/clubs/club-1/logo/00000000-0000-0000-0000-000000000001.png"
        )
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { opponentRepository.getOpponentById("opp-1") } returns opponent
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns ageGroup
        coEvery { seasonRepository.getSeasonById("s-1") } returns season
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns listOf(
            MatchCallUpWithPlayer(MatchCallUp("cu-1", matchId, "p-called", CallUpStatus.CALLED_UP), player("p-called"))
        )
        coEvery { playerRepository.getPlayersByAgeGroup(ag1) } returns listOf(player("p-called"), player("p-free"))
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 2, 1)
        coEvery { matchEventRepository.getEventsByMatch(matchId) } returns events
        coEvery { matchRepository.getLineupChanges(matchId) } returns lineupChanges
    }

    // A finished match's events: our goal (assisted), theirs, a substitution and a booking.
    private val events = listOf(
        MatchEvent("g1", matchId, EventType.GOAL, 10, playerId = "p-called", createdAt = Instant.fromEpochMilliseconds(10), period = 1),
        MatchEvent("a1", matchId, EventType.ASSIST, 10, playerId = "p-free", createdAt = Instant.fromEpochMilliseconds(10), period = 1, relatedEventId = "g1"),
        MatchEvent("g2", matchId, EventType.GOAL, 20, isOpponent = true, createdAt = Instant.fromEpochMilliseconds(20), period = 1),
        MatchEvent("so", matchId, EventType.SUBSTITUTION_OUT, 30, playerId = "p-called", createdAt = Instant.fromEpochMilliseconds(30), period = 2),
        MatchEvent("y1", matchId, EventType.YELLOW_CARD, 40, playerId = "p-called", createdAt = Instant.fromEpochMilliseconds(40), period = 2)
    )
    private val lineupChanges = listOf(MatchLineupChange(0, 1, "4-4-2", mapOf(0 to "p-called"), Instant.fromEpochMilliseconds(1)))

    private fun stubCoachOf(id: String): User {
        val coach = user(id)
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        return coach
    }

    @Test
    fun staffSeeAFinishedMatchsWholeRecord() = runBlocking {
        val coach = stubCoachOf("coach")
        baseStubs()
        coEvery { matchRepository.getMatchById(matchId) } returns match.copy(status = MatchStatus.FINISHED, fullTimeMinute = 90)
        coEvery { matchReportRepository.getByMatch(matchId) } returns
            MatchReport(matchId, updatedAt = Instant.fromEpochMilliseconds(5), updatedByUserId = "coach", completedAt = Instant.fromEpochMilliseconds(6))
        coEvery { matchPlayerStatsRepository.getByMatch(matchId) } returns listOf(MatchPlayerStats(matchId, "p-called", true, 30, 1, 0, 0, 1, 0, rating = 8))

        val d = useCase(coach.id, matchId)

        assertTrue(d.canSeeMatchRecord)
        assertEquals(events, d.events)
        assertEquals(8, d.playerStats.single().rating, "the report is completed, so its ratings count")
        assertEquals(lineupChanges, d.lineupChanges)
    }

    @Test
    fun aDraftReportsRatingsAreNotShownYet() = runBlocking {
        val coach = stubCoachOf("coach")
        baseStubs()
        coEvery { matchRepository.getMatchById(matchId) } returns match.copy(status = MatchStatus.FINISHED, fullTimeMinute = 90)
        coEvery { matchReportRepository.getByMatch(matchId) } returns MatchReport(matchId, updatedAt = Instant.fromEpochMilliseconds(5), updatedByUserId = "coach")
        coEvery { matchPlayerStatsRepository.getByMatch(matchId) } returns listOf(MatchPlayerStats(matchId, "p-called", true, 30, 1, 0, 0, 1, 0, rating = 8))

        assertEquals(null, useCase(coach.id, matchId).playerStats.single().rating)
    }

    @Test
    fun aMatchFinishedBeforeStoredStatsHasItsLinesWorkedOut() = runBlocking {
        val coach = stubCoachOf("coach")
        baseStubs()
        coEvery { matchRepository.getMatchById(matchId) } returns match.copy(status = MatchStatus.FINISHED, fullTimeMinute = 90)
        coEvery { matchReportRepository.getByMatch(matchId) } returns null
        coEvery { matchPlayerStatsRepository.getByMatch(matchId) } returns emptyList()

        val line = useCase(coach.id, matchId).playerStats.single()
        assertEquals("p-called", line.playerId)
        assertEquals(1, line.goals)
        assertEquals(1, line.yellowCards)
    }

    @Test
    fun aParentSeesOnlyTheGoalsAndTheirAssists() = runBlocking {
        val parent = user("parent")
        baseStubs()
        coEvery { matchRepository.getMatchById(matchId) } returns match.copy(status = MatchStatus.FINISHED)
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubId) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(player("p-called"))

        val d = useCase(parent.id, matchId)

        assertEquals(false, d.canSeeMatchRecord)
        assertEquals(listOf("g1", "a1", "g2"), d.events.map { it.id })
        assertTrue(d.playerStats.isEmpty())
        assertTrue(d.lineupChanges.isEmpty())
    }

    @Test
    fun noRecordBeforeFullTime() = runBlocking {
        val coach = stubCoachOf("coach")
        baseStubs()

        val d = useCase(coach.id, matchId)
        assertTrue(d.events.isEmpty())
        assertTrue(d.playerStats.isEmpty())
        assertTrue(d.lineupChanges.isEmpty())
    }

    @Test
    fun coachSeesDetailsWithPoolMinusCalledUpAndCanManageEverything() = runBlocking {
        val coach = user("coach")
        baseStubs()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH

        val d = useCase(coach.id, matchId)

        assertEquals("Sub-15", d.ageGroup.name)
        assertEquals("26/27", d.season.name)
        assertEquals(listOf("p-called"), d.callUps.map { it.player.id })
        // Pool is the age group minus whoever is already called up.
        assertEquals(listOf("p-free"), d.poolPlayers.map { it.id })
        // A coach clears all three default thresholds (MANAGE_MATCH/MANAGE_CALL_UPS default to
        // Coach, MANAGE_LIVE_MATCH defaults to Staff, which Coach also satisfies).
        assertTrue(d.canManageMatch)
        assertTrue(d.canManageCallUps)
        assertTrue(d.canManageLiveMatch)
        assertEquals(MatchScore(matchId, 2, 1), d.score)
        // The club's logo, for the header next to the opponent's.
        assertEquals("/clubs/club-1/logo/00000000-0000-0000-0000-000000000001.png", d.clubLogoUrl)
    }

    @Test
    fun ageGroupStaffCanManageLiveMatchOnlyByDefault() = runBlocking {
        val staff = user("staff")
        baseStubs()
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, ag1) } returns AgeGroupRole.STAFF

        val d = useCase(staff.id, matchId)

        // By default, staff can run live-match ops but not edit the match's own record or manage
        // call-ups -- both of those stay coach-only by default (see ConfigurablePermission).
        assertFalse(d.canManageMatch)
        assertFalse(d.canManageCallUps)
        assertTrue(d.canManageLiveMatch)
    }

    @Test
    fun outsiderIsDenied() = runBlocking {
        val outsider = user("outsider")
        baseStubs()
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubId) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(outsider.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(outsider.id) } returns emptyList()

        assertFailsWith<ForbiddenException> { useCase(outsider.id, matchId) }
        Unit
    }

    private fun stubCoach(): User {
        val coach = user("coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        return coach
    }

    @Test
    fun aFinishedMatchCarriesTheReportSummaryForStaff() = runBlocking {
        val coach = stubCoach()
        baseStubs()
        coEvery { matchRepository.getMatchById(matchId) } returns match.copy(status = MatchStatus.FINISHED, fullTimeMinute = 90)
        val nuno = User("nuno", "n@t.com", firstName = "Nuno", lastName = "Ferreira", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById("nuno") } returns nuno
        coEvery { matchReportRepository.getByMatch(matchId) } returns
            MatchReport(matchId, tactic = "4-4-2", updatedAt = Instant.fromEpochMilliseconds(5), updatedByUserId = "nuno")
        coEvery { matchPlayerStatsRepository.getByMatch(matchId) } returns listOf(
            MatchPlayerStats(matchId, "p1", true, 90, 0, 0, 0, 0, 0, rating = 7),
            MatchPlayerStats(matchId, "p2", false, 0, 0, 0, 0, 0, 0)
        )

        val report = useCase(coach.id, matchId).report!!

        assertEquals(MatchReportStatus.DRAFT, report.status)
        assertEquals("Nuno Ferreira", report.updatedByName)
        assertEquals(1, report.ratedPlayers)
        assertEquals(2, report.ratablePlayers)
        assertTrue(report.canEdit)
    }

    @Test
    fun noReportSummaryBeforeFullTime() = runBlocking {
        val coach = stubCoach()
        baseStubs()

        assertEquals(null, useCase(coach.id, matchId).report)
    }

    @Test
    fun aParentGetsNoReportSummary() = runBlocking {
        val parent = user("parent")
        baseStubs()
        coEvery { matchRepository.getMatchById(matchId) } returns match.copy(status = MatchStatus.FINISHED)
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubId) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(player("p-called"))
        coEvery { playerRepository.getPlayersByAgeGroup(ag1) } returns listOf(player("p-called"), player("p-free"))

        assertEquals(null, useCase(parent.id, matchId).report)
    }
}
