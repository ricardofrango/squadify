package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.service.ClubPushAudience
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.service.GoalPush
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.Player
import kotlinx.datetime.LocalDate
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
    private val playerRepository = mockk<PlayerRepository>()
    private val publicImageLinks = PublicImageLinks("https://api.example.com", "secret", clock = object : kotlin.time.Clock {
        override fun now() = Instant.fromEpochSeconds(1_000)
    })
    private val useCase = NotifyMatchEventUseCase(
        matchRepository,
        matchEventRepository,
        teamRepository,
        opponentRepository,
        ClubPushAudience(clubRepository, mockk()),
        fcmTokenRepository,
        pushNotificationService,
        transactionManager,
        playerRepository,
        publicImageLinks
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
    fun whoeverCausedItIsntTold() = runBlocking {
        stubHappyPath(listOf("token-1"))
        coEvery { fcmTokenRepository.getTokensForUsers(emptyList()) } returns emptyMap()

        // u1, the only member with a device, scored: nobody else to tell.
        useCase(matchId, MatchNotificationEvent.GOAL, causedBy = "u1")

        coVerify(exactly = 0) { pushNotificationService.sendGoalScored(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun goalPushesWithTheCurrentScore() = runBlocking {
        stubHappyPath(listOf("token-1"))

        useCase(matchId, MatchNotificationEvent.GOAL)

        coVerify { pushNotificationService.sendGoalScored(listOf("token-1"), PushLink.match(clubId, matchId), team.name, opponent.name, MatchScore(matchId, 1, 0), GoalPush()) }
    }

    private val photoId = "11111111-1111-1111-1111-111111111111"
    private val logoId = "22222222-2222-2222-2222-222222222222"
    private fun goal(type: EventType = EventType.GOAL, playerId: String? = null, isOpponent: Boolean = false) =
        MatchEvent("e-1", matchId, type, 12, playerId = playerId, isOpponent = isOpponent, createdAt = Instant.fromEpochMilliseconds(1))
    private fun player(photoUrl: String?) = Player("p-1", clubId, "Rodrigo", "Costa", LocalDate(2013, 1, 1), photoUrl = photoUrl, active = true)
    private fun sentGoal(): GoalPush {
        var sent: GoalPush? = null
        coVerify { pushNotificationService.sendGoalScored(any(), any(), any(), any(), any(), match { sent = it; true }) }
        return sent!!
    }

    @Test
    fun ourGoalNamesTheScorerWithASignedLinkToTheirPhoto() = runBlocking {
        stubHappyPath(listOf("token-1"))
        coEvery { playerRepository.getPlayerById("p-1") } returns player("/players/p-1/photo/$photoId.png")

        useCase(matchId, MatchNotificationEvent.GOAL, goal(playerId = "p-1"))

        val sent = sentGoal()
        kotlin.test.assertTrue(sent.forUs)
        kotlin.test.assertEquals("Rodrigo Costa", sent.scorerName)
        val url = sent.imageUrl!!
        kotlin.test.assertTrue(url.startsWith("https://api.example.com/v1/public/players/p-1/photo/$photoId.png?exp=87400&sig="))
        kotlin.test.assertTrue(publicImageLinks.isValidPhotoLink("p-1", photoId, 87_400, url.substringAfter("sig=")))
    }

    @Test
    fun ourGoalWithoutAScorerNamesNobody() = runBlocking {
        stubHappyPath(listOf("token-1"))

        // Logged from the score box: no player. The push stays "Goal! A 1 - 0 B" with the club's logo.
        useCase(matchId, MatchNotificationEvent.GOAL, goal(playerId = null))

        kotlin.test.assertEquals(GoalPush(forUs = true, scorerName = null, imageUrl = null), sentGoal())
        coVerify(exactly = 0) { playerRepository.getPlayerById(any()) }
    }

    @Test
    fun ourScorerWithoutAPhotoHasNoPicture() = runBlocking {
        stubHappyPath(listOf("token-1"))
        coEvery { playerRepository.getPlayerById("p-1") } returns player(photoUrl = null)

        useCase(matchId, MatchNotificationEvent.GOAL, goal(playerId = "p-1"))

        kotlin.test.assertEquals(GoalPush(forUs = true, scorerName = "Rodrigo Costa", imageUrl = null), sentGoal())
    }

    @Test
    fun theirGoalShowsTheOpponentsLogoWhenTheyHaveOne() = runBlocking {
        stubHappyPath(listOf("token-1"))
        coEvery { opponentRepository.getOpponentById("opp-1") } returns opponent.copy(logoUrl = "/opponents/opp-1/logo/$logoId.png")

        useCase(matchId, MatchNotificationEvent.GOAL, goal(isOpponent = true))

        kotlin.test.assertEquals(GoalPush(forUs = false, imageUrl = "https://api.example.com/v1/public/opponents/opp-1/logo/$logoId.png"), sentGoal())
    }

    @Test
    fun ownGoalsCountForTheOtherSide() = runBlocking {
        stubHappyPath(listOf("token-1"))

        // Our player's own goal is theirs (no logo here); theirs is ours, with no scorer of ours to name.
        useCase(matchId, MatchNotificationEvent.GOAL, goal(EventType.OWN_GOAL, playerId = "p-1"))
        kotlin.test.assertEquals(GoalPush(forUs = false), sentGoal())
        io.mockk.clearMocks(pushNotificationService)
        useCase(matchId, MatchNotificationEvent.GOAL, goal(EventType.OWN_GOAL, isOpponent = true))
        kotlin.test.assertEquals(GoalPush(forUs = true), sentGoal())
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
