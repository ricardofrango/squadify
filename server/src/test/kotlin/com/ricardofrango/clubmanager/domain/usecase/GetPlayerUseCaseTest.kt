package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.MatchEventRepository
import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.domain.repository.OpponentRepository
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.domain.repository.UserRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ForbiddenException
import com.ricardofrango.clubmanager.exception.NotFoundException
import com.ricardofrango.clubmanager.model.AgeGroup
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.EventType
import com.ricardofrango.clubmanager.model.Match
import com.ricardofrango.clubmanager.model.MatchEvent
import com.ricardofrango.clubmanager.model.MatchStatus
import com.ricardofrango.clubmanager.model.Opponent
import com.ricardofrango.clubmanager.model.Player
import com.ricardofrango.clubmanager.model.User
import com.ricardofrango.clubmanager.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class GetPlayerUseCaseTest {

    private val playerRepository = mockk<PlayerRepository>()
    private val matchEventRepository = mockk<MatchEventRepository>()
    private val matchRepository = mockk<MatchRepository>()
    private val opponentRepository = mockk<OpponentRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager
    )
    private val useCase = GetPlayerUseCase(
        playerRepository, matchEventRepository, matchRepository, opponentRepository, ageGroupRepository,
        checkUserPermissionUseCase, transactionManager
    )

    private val clubId = "club-1"
    private val playerId = "player-1"
    private val ag1 = "ag-1"

    private val player = Player(
        id = playerId, clubId = clubId, firstName = "Tomas", lastName = "Braganca",
        birthDate = LocalDate(2011, 3, 12), shirtNumber = 10, active = true
    )

    private fun member() = User(
        id = "u1", email = "u1@test.com", firstName = "T", lastName = "U",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun stubBaseReads() {
        coEvery { playerRepository.getPlayerById(playerId) } returns player
        coEvery { playerRepository.getAgeGroupIdsByPlayer(playerId) } returns listOf(ag1)
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(
            id = ag1, clubId = clubId, name = "Sub 15", seasonId = "s1", birthYear = 2011
        )
        coEvery { matchRepository.countCallUpsByPlayer(playerId) } returns 14L
        coEvery { playerRepository.countGuardiansByPlayer(playerId) } returns 2L
    }

    @Test
    fun aggregatesGoalsAssistsAndRecentEventsFromAllEvents() = runBlocking {
        val member = member()
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER
        stubBaseReads()

        val goal = MatchEvent(id = "e1", matchId = "m1", type = EventType.GOAL, minute = 63, createdAt = Instant.fromEpochMilliseconds(3000))
        val assist = MatchEvent(id = "e2", matchId = "m1", type = EventType.ASSIST, minute = 71, createdAt = Instant.fromEpochMilliseconds(2000))
        val yellow = MatchEvent(id = "e3", matchId = "m2", type = EventType.YELLOW_CARD, minute = 44, createdAt = Instant.fromEpochMilliseconds(1000))
        coEvery { matchEventRepository.getAllEventsByPlayer(playerId) } returns listOf(goal, assist, yellow)

        val match1 = Match(id = "m1", clubId = clubId, teamId = "t1", opponentId = "opp1", dateTime = Instant.fromEpochMilliseconds(500_000), status = MatchStatus.FINISHED)
        val match2 = Match(id = "m2", clubId = clubId, teamId = "t1", opponentId = "opp2", dateTime = Instant.fromEpochMilliseconds(400_000), status = MatchStatus.FINISHED)
        coEvery { matchRepository.getMatchById("m1") } returns match1
        coEvery { matchRepository.getMatchById("m2") } returns match2
        coEvery { opponentRepository.getOpponentById("opp1") } returns Opponent(id = "opp1", clubId = clubId, name = "AD Fontelas")
        coEvery { opponentRepository.getOpponentById("opp2") } returns Opponent(id = "opp2", clubId = clubId, name = "GD Marinhas")

        val result = useCase(member.id, playerId)

        assertEquals(1, result.goals)
        assertEquals(1, result.assists)
        assertEquals(14, result.timesCalledUp)
        assertEquals(2, result.guardiansLinked)
        assertEquals("Sub 15", result.ageGroupLabel)
        assertEquals(3, result.recentEvents.size)
        assertEquals("AD Fontelas", result.recentEvents.first().opponentName)
        Unit
    }

    @Test
    fun aPlainClubMemberCanReadAnyPlayerInTheClub() = runBlocking {
        val member = member()
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER
        stubBaseReads()
        coEvery { matchEventRepository.getAllEventsByPlayer(playerId) } returns emptyList()

        val result = useCase(member.id, playerId)

        assertEquals(playerId, result.player.id)
        Unit
    }

    @Test
    fun aUserOutsideTheClubIsDenied() = runBlocking {
        val outsider = member()
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubId) } returns null
        coEvery { playerRepository.getPlayerById(playerId) } returns player

        assertFailsWith<ForbiddenException> { useCase(outsider.id, playerId) }
        Unit
    }

    @Test
    fun aMissingPlayerIs404() = runBlocking {
        coEvery { playerRepository.getPlayerById("nope") } returns null
        assertFailsWith<NotFoundException> { useCase("anyone", "nope") }
        Unit
    }
}
