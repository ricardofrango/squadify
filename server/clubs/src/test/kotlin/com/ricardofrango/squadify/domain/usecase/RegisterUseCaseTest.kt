package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Invite
import com.ricardofrango.squadify.model.InviteType
import com.ricardofrango.squadify.model.RegisterRequest
import com.ricardofrango.squadify.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.time.Instant

/**
 * Registering resolves the new user's pending club invites into memberships. A user holds at most
 * one membership per club, so several invites for the same club collapse into one, at the highest
 * invited role.
 */
class RegisterUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val playerRepository = mockk<PlayerRepository>(relaxed = true)
    private val inviteRepository = mockk<InviteRepository>(relaxed = true)
    private val clubRepository = mockk<ClubRepository>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    // As the clubs server wires it: signing up accepts the invites waiting for that email.
    private val useCase = RegisterUseCase(
        userRepository, transactionManager, listOf(AcceptInvitesOnRegister(playerRepository, inviteRepository, clubRepository))
    )

    private val email = "new@test.com"
    private val request = RegisterRequest(email = email, password = "password1", firstName = "New", lastName = "User")

    private fun invite(id: String, clubId: String, role: ClubRole) = Invite(
        id = id, email = email, clubId = clubId, type = InviteType.CLUB_MEMBER, role = role,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun stubUserCreation() {
        coEvery { userRepository.findByEmail(email) } returns null
        coEvery { userRepository.createUser(any(), any()) } answers { firstArg<User>().copy(id = "new-user") }
    }

    @Test
    fun severalInvitesForTheSameClubBecomeOneMembershipAtTheHighestRole() = runBlocking {
        stubUserCreation()
        coEvery { inviteRepository.findByEmail(email) } returns listOf(
            invite("i1", "club-a", ClubRole.MEMBER),
            invite("i2", "club-a", ClubRole.CLUB_ADMIN),
            invite("i3", "club-a", ClubRole.COORDINATOR)
        )

        useCase(request)

        coVerify(exactly = 1) { clubRepository.addMember("club-a", "new-user", ClubRole.CLUB_ADMIN) }
        coVerify(exactly = 1) { clubRepository.addMember(any(), any(), any()) }
        coVerify { inviteRepository.deleteInvites(listOf("i1", "i2", "i3")) }
    }

    @Test
    fun invitesForDifferentClubsEachBecomeAMembership() = runBlocking {
        stubUserCreation()
        coEvery { inviteRepository.findByEmail(email) } returns listOf(
            invite("i1", "club-a", ClubRole.MEMBER),
            invite("i2", "club-b", ClubRole.COORDINATOR)
        )

        useCase(request)

        coVerify(exactly = 1) { clubRepository.addMember("club-a", "new-user", ClubRole.MEMBER) }
        coVerify(exactly = 1) { clubRepository.addMember("club-b", "new-user", ClubRole.COORDINATOR) }
    }
}
