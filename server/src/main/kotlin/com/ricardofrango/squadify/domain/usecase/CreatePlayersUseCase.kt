package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class CreatePlayersUseCase(
    private val playerRepository: PlayerRepository,
    private val userRepository: UserRepository,
    private val inviteRepository: InviteRepository,
    private val clubRepository: ClubRepository,
    private val transactionManager: TransactionManager,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, request: CreatePlayersRequest): List<Player> = transactionManager.runInTransaction {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = request.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Insufficient permissions to create players"
        )

        // 1. Create players in batch
        val playersToCreate = request.players.map {
            if (it.firstName.isBlank() || it.lastName.isBlank()) {
                throw ValidationException("Player name cannot be empty")
            }
            Player(
                id = Uuid.random().toString(),
                clubId = request.clubId,
                firstName = it.firstName,
                lastName = it.lastName,
                birthDate = it.birthDate,
                shirtNumber = it.shirtNumber,
                photoUrl = it.photoUrl,
                active = true,
                position = it.position,
                heightCm = it.heightCm,
                weightKg = it.weightKg,
                emergencyContact = it.emergencyContact
            )
        }

        val createdPlayers = playerRepository.createPlayers(playersToCreate)

        // 2. Map request data to created players by index/logic (since they are in same order)
        val parentLinksToCreate = mutableListOf<Triple<String, String, UserPlayerRelationship>>()
        val parentInvitesToCreate = mutableListOf<Invite>()
        val playerInvitesToCreate = mutableListOf<Invite>()
        val directClubMemberships = mutableListOf<Triple<String, String, ClubRole>>()

        // Collect all unique emails to check in batch
        val allEmails = request.players.flatMap { p ->
            p.parents.map { it.email } + listOfNotNull(p.email)
        }.distinct()

        val existingUsersMap = userRepository.findByEmails(allEmails).associateBy { it.email }
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

        request.players.forEachIndexed { index, playerReq ->
            val createdPlayer = createdPlayers[index]

            // Handle player self-invite
            playerReq.email?.let { pEmail ->
                val user = existingUsersMap[pEmail]
                if (user != null) {
                    directClubMemberships.add(Triple(request.clubId, user.id, ClubRole.MEMBER))
                } else {
                    playerInvitesToCreate.add(
                        Invite(
                            id = Uuid.random().toString(),
                            email = pEmail,
                            clubId = request.clubId,
                            type = InviteType.CLUB_MEMBER,
                            role = ClubRole.MEMBER,
                            createdAt = now
                        )
                    )
                }
            }

            // Handle parents
            playerReq.parents.forEach { parentReq ->
                val user = existingUsersMap[parentReq.email]
                if (user != null) {
                    parentLinksToCreate.add(
                        Triple(
                            user.id,
                            createdPlayer.id,
                            parentReq.relationship
                        )
                    )
                } else {
                    parentInvitesToCreate.add(
                        Invite(
                            id = Uuid.random().toString(),
                            email = parentReq.email,
                            clubId = request.clubId,
                            type = InviteType.PARENT_PLAYER,
                            playerId = createdPlayer.id,
                            relationship = parentReq.relationship,
                            createdAt = now
                        )
                    )
                }
            }
        }

        // 3. Batch operations for associations and invites
        if (directClubMemberships.isNotEmpty()) {
            directClubMemberships.forEach { (cId, uId, role) ->
                clubRepository.addMember(cId, uId, role)
            }
        }
        if (parentLinksToCreate.isNotEmpty()) {
            playerRepository.createUserPlayers(parentLinksToCreate)
        }
        val allInvites = playerInvitesToCreate + parentInvitesToCreate
        if (allInvites.isNotEmpty()) {
            inviteRepository.createInvites(allInvites)
        }

        createdPlayers
    }
}
