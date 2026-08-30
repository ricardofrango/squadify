package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.InviteRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class CreatePlayerUseCase(
    private val playerRepository: PlayerRepository,
    private val userRepository: UserRepository,
    private val inviteRepository: InviteRepository,
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, request: CreatePlayerRequest): Player {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = request.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Insufficient permissions to create players"
        )

        if (request.firstName.isBlank() || request.lastName.isBlank()) {
            throw ValidationException("Player name cannot be empty")
        }

        val player = Player(
            id = Uuid.random().toString(),
            clubId = request.clubId,
            firstName = request.firstName,
            lastName = request.lastName,
            birthDate = request.birthDate,
            shirtNumber = request.shirtNumber,
            photoUrl = request.photoUrl,
            active = true
        )

        val createdPlayer = playerRepository.createPlayer(player)

        // Invite the player themselves if email is provided
        request.email?.let { playerEmail ->
            val existingUser = userRepository.findByEmail(playerEmail)
            if (existingUser != null) {
                clubRepository.addMember(request.clubId, existingUser.id, ClubRole.MEMBER)
            } else {
                inviteRepository.createInvite(
                    Invite(
                        id = Uuid.random().toString(),
                        email = playerEmail,
                        clubId = request.clubId,
                        type = InviteType.CLUB_MEMBER,
                        role = ClubRole.MEMBER,
                        createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                    )
                )
            }
        }

        if (request.parents.isNotEmpty()) {
            val parentEmails = request.parents.map { it.email }
            val existingUsers = userRepository.findByEmails(parentEmails).associateBy { it.email }

            request.parents.forEach { parentRequest ->
                val user = existingUsers[parentRequest.email]
                if (user != null) {
                    playerRepository.createUserPlayer(
                        userId = user.id,
                        playerId = createdPlayer.id,
                        relationship = parentRequest.relationship
                    )
                } else {
                    inviteRepository.createInvite(
                        Invite(
                            id = Uuid.random().toString(),
                            email = parentRequest.email,
                            clubId = request.clubId,
                            type = InviteType.PARENT_PLAYER,
                            playerId = createdPlayer.id,
                            relationship = parentRequest.relationship,
                            createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        return createdPlayer
    }
}
