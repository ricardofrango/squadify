package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.ClubPushAudience
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.AgeGroupStaffMember
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.ClubMemberSummary
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ManageCallUpRequest
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.model.Team
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

// "Call-up ready" (the developer, 2026-10-31): sent when a call-up is saved with at least the match's
// size and something changed, to the age group's staff on the clubs server, never to whoever saved it.
class CallUpReadyPushTest {
    private val matchRepository = mockk<MatchRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val opponentRepository = mockk<OpponentRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val clubRepository = mockk<ClubRepository>()
    private val fcmTokenRepository = mockk<FcmTokenRepository>()
    private val push = mockk<PushNotificationService>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val useCase = ManageCallUpsUseCase(
        matchRepository, transactionManager, mockk<AccessPolicy>(relaxed = true), playerRepository,
        teamRepository, opponentRepository, fcmTokenRepository, push, ClubPushAudience(clubRepository, ageGroupRepository)
    )

    private val match = Match("m1", "c1", "t1", "o1", dateTime = Instant.fromEpochMilliseconds(0), teamSize = 3)
    private fun player(id: String) = Player(id, "c1", "P", id, LocalDate(2014, 1, 1), active = true)
    private fun calledUp(vararg ids: String) = ids.map { MatchCallUpWithPlayer(MatchCallUp("cu-$it", "m1", it, CallUpStatus.CALLED_UP), player(it)) }

    init {
        coEvery { teamRepository.getTeamById("t1") } returns Team("t1", "c1", "ag1", "s1", "Sub-13 A", null, true)
        coEvery { opponentRepository.getOpponentById("o1") } returns Opponent("o1", "c1", "Fontelas", null, null)
        coEvery { playerRepository.getPlayersData(any()) } answers { firstArg<List<String>>().map(::player) }
        coEvery { matchRepository.manageCallUps(any(), any()) } returns true
        coEvery { ageGroupRepository.getAgeGroupStaff("ag1") } returns listOf(
            AgeGroupStaffMember("coach", "Ana", "C", AgeGroupRole.COACH),
            AgeGroupStaffMember("staff", "Rui", "S", AgeGroupRole.STAFF)
        )
        // The rest of the club: never told about a call-up.
        coEvery { clubRepository.getClubMembers("c1") } returns listOf(ClubMemberSummary("parent", "P", "P", "p@t.com", ClubRole.MEMBER))
        coEvery { fcmTokenRepository.getTokensForUsers(any()) } answers { firstArg<List<String>>().associateWith { listOf("token-$it") } }
    }

    private fun save(by: String, vararg ids: String, before: List<String> = emptyList(), status: MatchStatus = MatchStatus.SCHEDULED) = runBlocking {
        coEvery { matchRepository.getMatchById("m1") } returns match.copy(status = status)
        coEvery { matchRepository.getCallUpsForMatch("m1") } returns calledUp(*before.toTypedArray())
        useCase(by, "m1", ManageCallUpRequest(ids.toList()))
    }

    @Test
    fun aReadyCallUpGoesToTheAgeGroupsStaffButNotWhoSavedIt() {
        save("coach", "p1", "p2", "p3")
        coVerify(exactly = 1) { push.sendCallUpReady(listOf("token-staff"), PushLink.match("c1", "m1"), "Sub-13 A", "Fontelas", 3) }
    }

    @Test
    fun savedAgainWithChangesItGoesAgain() {
        save("coach", "p1", "p2", "p3", "p4", before = listOf("p1", "p2", "p3"))
        coVerify(exactly = 1) { push.sendCallUpReady(any(), any(), any(), any(), 4) }
    }

    @Test
    fun belowTheMatchsSizeUnchangedOrAfterKickoffNothingIsSent() {
        save("coach", "p1", "p2")
        save("coach", "p1", "p2", "p3", before = listOf("p1", "p2", "p3"))
        save("coach", "p1", "p2", "p3", "p4", before = listOf("p1", "p2", "p3"), status = MatchStatus.LIVE)
        coVerify(exactly = 0) { push.sendCallUpReady(any(), any(), any(), any(), any()) }
    }

    @Test
    fun aFailedLookupStillSavesTheCallUp() {
        coEvery { teamRepository.getTeamById("t1") } throws IllegalStateException("db")
        assertEquals(true, save("coach", "p1", "p2", "p3"))
        coVerify(exactly = 0) { push.sendCallUpReady(any(), any(), any(), any(), any()) }
    }
}
