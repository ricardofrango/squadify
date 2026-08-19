package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Club
import com.ricardofrango.footballmanager.model.ClubStatus
import com.ricardofrango.footballmanager.model.Role
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ProvisionClubUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(executorId: String, directorId: String, clubName: String): Boolean {
        // 1. Check if executor is Platform Admin
        val executor = userRepository.findById(executorId)
            ?: throw ForbiddenException("Executor not found")
        
        if (!executor.isPlatformAdmin) {
            throw ForbiddenException("Only platform admins can provision new clubs")
        }

        // 2. Check if director exists
        userRepository.findById(directorId)
            ?: throw ValidationException("Director user does not exist")

        // 3. Create the club
        val clubId = Uuid.random().toString()
        val club = Club(
            id = clubId,
            name = clubName,
            status = ClubStatus.ACTIVE,
            createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        )
        
        val clubCreated = clubRepository.createClub(club)
        if (!clubCreated) return false

        // 4. Assign director as CLUB_ADMIN
        return clubRepository.addMember(clubId, directorId, Role.CLUB_ADMIN)
    }
}
