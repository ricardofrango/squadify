package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.*
import org.mindrot.jbcrypt.BCrypt
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RegisterUseCase(
    private val userRepository: UserRepository,
    private val playerRepository: PlayerRepository,
    private val inviteRepository: InviteRepository,
    private val clubRepository: ClubRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(request: RegisterRequest): User = transactionManager.runInTransaction {
        if (request.email.isBlank() || request.password.isBlank()) {
            throw ValidationException("Email and password are required")
        }

        if (userRepository.findByEmail(request.email) != null) {
            throw ConflictException("User with this email already exists")
        }

        val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())

        val user = User(
            id = Uuid.random().toString(),
            email = request.email,
            phone = request.phone,
            firstName = request.firstName,
            lastName = request.lastName,
            status = UserStatus.ACTIVE,
            createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        )

        val createdUser = userRepository.createUser(user, passwordHash)

        // Process pending invites
        val invites = inviteRepository.findByEmail(createdUser.email)
        if (invites.isNotEmpty()) {
            val clubMemberships = mutableListOf<Pair<String, ClubRole>>()
            val parentLinks = mutableListOf<Triple<String, String, UserPlayerRelationship>>()

            invites.forEach { invite ->
                when (invite.type) {
                    InviteType.CLUB_MEMBER -> {
                        invite.role
                            ?.let { invite.clubId to it }
                            ?.run(clubMemberships::add)
                    }

                    InviteType.PARENT_PLAYER -> {
                        val playerId = invite.playerId
                        val relationship = invite.relationship
                        if (playerId != null && relationship != null) {
                            parentLinks.add(Triple(createdUser.id, playerId, relationship))
                        }
                    }
                }
            }

            // Several invites can target the same club (e.g. invited as a member, then separately as
            // its director). A user holds one membership per club, so keep the highest role per club.
            clubMemberships
                .groupBy({ it.first }, { it.second })
                .forEach { (clubId, roles) ->
                    clubRepository.addMember(clubId, createdUser.id, roles.maxBy { it.level })
                }
            if (parentLinks.isNotEmpty()) {
                playerRepository.createUserPlayers(parentLinks)
            }

            inviteRepository.deleteInvites(invites.map { it.id })
        }

        createdUser
    }
}
