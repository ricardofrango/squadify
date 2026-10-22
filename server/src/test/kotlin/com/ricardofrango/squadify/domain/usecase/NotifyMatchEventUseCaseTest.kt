package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.model.ClubMemberSummary
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Team
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.time.Instant

/**
 * This use case must never throw (it's called after its trigger's own transaction has already
 * committed -- see its own doc comment) and must never notify when there's nothing to notify with
 * (no match/team/opponent found, or no club member has a registered device).
 */
class NotifyMatchEventUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val matchEventRepository = mockk<MatchEventRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val opponentRepository = mockk<OpponentRepository>()
    private val clubRepository = mockk<ClubRepository>()
    private val fcmTokenRepository = mockk<FcmTokenRepository>()
    private val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val useCase = NotifyMatchEventUseCase(
        matchRepository,
        matchEventRepository,
        teamRepository,
        opponentRepository,
        clubRepository,
        fcmTokenRepository,
        pushNotificationService,
        transactionManager
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val match = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE)
    private val team = Team(teamId, clubId, "ag-1", "s-1", "Sub-15 A", "A", active = true)
    private val opponent = Opponent("opp-1", clubId, "Rivals FC")

    private fun stubHappyPath(tokens: List<String>) {
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { opponentRepository.getOpponentById("opp-1") } returns opponent
        coEvery { clubRepository.getClubMembers(clubId) } returns listOf(
            ClubMemberSummary("u1", "A", "B", "a@b.com", ClubRole.MEMBER)
        )
        coEvery { fcmTokenRepository.getTokensForUsers(listOf("u1")) } returns mapOf("u1" to tokens)
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 1, 0)
    }

    @Test
    fun kickoffPushesToEveryClubMemberWithAToken() = runBlocking {
        stubHappyPath(listOf("token-1"))

        useCase(matchId, MatchNotificationEvent.KICKOFF)

        coVerify { pushNotificationService.sendMatchKickedOff(listOf("token-1"), PushLink.match(clubId, matchId), team.name, opponent.name) }
    }

    @Test
    fun goalPushesWithTheCurrentScore() = runBlocking {
        stubHappyPath(listOf("token-1"))

        useCase(matchId, MatchNotificationEvent.GOAL)

        coVerify { pushNotificationService.sendGoalScored(listOf("token-1"), PushLink.match(clubId, matchId), team.name, opponent.name, MatchScore(matchId, 1, 0)) }
    }

    @Test
    fun scheduledPushesToEveryClubMemberWithAToken() = runBlocking {
        stubHappyPath(listOf("token-1"))

        useCase(matchId, MatchNotificationEvent.SCHEDULED)

        coVerify { pushNotificationService.sendMatchScheduled(listOf("token-1"), PushLink.match(clubId, matchId), team.name, opponent.name) }
    }

    @Test
    fun rescheduledPushesToEveryClubMemberWithAToken() = runBlocking {
        stubHappyPath(listOf("token-1"))

        useCase(matchId, MatchNotificationEvent.RESCHEDULED)

        coVerify { pushNotificationService.sendMatchRescheduled(listOf("token-1"), PushLink.match(clubId, matchId), team.name, opponent.name) }
    }

    @Test
    fun cancelledPushesToEveryClubMemberWithAToken() = runBlocking {
        stubHappyPath(listOf("token-1"))

        useCase(matchId, MatchNotificationEvent.CANCELLED)

        coVerify { pushNotificationService.sendMatchCancelled(listOf("token-1"), PushLink.match(clubId, matchId), team.name, opponent.name) }
    }

    @Test
    fun noClubMemberHasATokenSendsNothing() = runBlocking {
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { opponentRepository.getOpponentById("opp-1") } returns opponent
        coEvery { clubRepository.getClubMembers(clubId) } returns listOf(
            ClubMemberSummary("u1", "A", "B", "a@b.com", ClubRole.MEMBER)
        )
        coEvery { fcmTokenRepository.getTokensForUsers(listOf("u1")) } returns emptyMap()

        useCase(matchId, MatchNotificationEvent.FULL_TIME)

        coVerify(exactly = 0) { pushNotificationService.sendMatchFinished(any(), any(), any(), any(), any()) }
    }

    @Test
    fun aNonexistentMatchNeverThrows() = runBlocking {
        coEvery { matchRepository.getMatchById("no-such-match") } returns null

        useCase("no-such-match", MatchNotificationEvent.HALF_TIME)

        coVerify(exactly = 0) { pushNotificationService.sendHalfTime(any(), any(), any(), any(), any()) }
    }

    @Test
    fun aRepositoryFailureNeverThrows() = runBlocking {
        coEvery { matchRepository.getMatchById(matchId) } throws RuntimeException("db exploded")

        // Must not propagate -- this runs after the triggering use case's own transaction already
        // committed, so a failure here must never surface to that caller.
        useCase(matchId, MatchNotificationEvent.SECOND_HALF_STARTED)

        coVerify(exactly = 0) { pushNotificationService.sendSecondHalfStarted(any(), any(), any(), any()) }
    }
}
