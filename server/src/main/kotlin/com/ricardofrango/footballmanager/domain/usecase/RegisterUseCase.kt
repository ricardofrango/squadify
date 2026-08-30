package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.InviteRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ConflictException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.InviteType
import com.ricardofrango.footballmanager.model.RegisterRequest
import com.ricardofrango.footballmanager.model.TeamStaff
import com.ricardofrango.footballmanager.model.User
import com.ricardofrango.footballmanager.model.UserStatus
import org.mindrot.jbcrypt.BCrypt
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RegisterUseCase(
    private val userRepository: UserRepository,
    private val playerRepository: PlayerRepository,
    private val inviteRepository: InviteRepository,
    private val clubRepository: ClubRepository,
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(request: RegisterRequest): User {
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
        invites.forEach { invite ->
            when (invite.type) {
                InviteType.CLUB_MEMBER -> {
                    invite.role?.let { role ->
                        clubRepository.addMember(invite.clubId, createdUser.id, role)
                    }
                }

                InviteType.TEAM_MEMBER -> {
                    // 1. Add to club
                    invite.role?.let { role ->
                        clubRepository.addMember(invite.clubId, createdUser.id, role)
                    }
                    // 2. Add to team
                    val teamId = invite.teamId
                    val teamRole = invite.teamRole
                    if (teamId != null && teamRole != null) {
                        teamRepository.addStaffToTeam(
                            TeamStaff(
                                id = Uuid.random().toString(),
                                teamId = teamId,
                                userId = createdUser.id,
                                role = teamRole
                            )
                        )
                    }
                }

                InviteType.PARENT_PLAYER -> {
                    val playerId = invite.playerId
                    val relationship = invite.relationship
                    if (playerId != null && relationship != null) {
                        playerRepository.createUserPlayer(
                            userId = createdUser.id,
                            playerId = playerId,
                            relationship = relationship
                        )
                    }
                }
            }
            inviteRepository.deleteInvite(invite.id)
        }

        return createdUser
    }
}
