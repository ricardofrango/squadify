package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.UpdateAgeGroupRequest

/**
 * Edit an age group: name, birth-year span and default team size. The same rules as creating one
 * (CreateAgeGroupsUseCase) apply, plus one of its own: the new span can't leave a player already in
 * the pool ineligible (born before the new oldest year) -- they'd silently stop being allowed to play.
 */
class UpdateAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val clubRepository: ClubRepository,
    private val playerRepository: PlayerRepository
) {
    suspend operator fun invoke(
        userId: String,
        ageGroupId: String,
        request: UpdateAgeGroupRequest
    ): Boolean = transactionManager.runInTransaction {
        val existingAgeGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
            ?: throw NotFoundException("Age group not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = existingAgeGroup.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can update age groups"
        )

        if (request.name?.isBlank() == true) {
            throw ValidationException("Age group name cannot be empty")
        }

        val updatedAgeGroup = existingAgeGroup.copy(
            name = request.name?.trim() ?: existingAgeGroup.name,
            startYear = request.startYear ?: existingAgeGroup.startYear,
            endYear = request.endYear ?: existingAgeGroup.endYear,
            teamSize = request.teamSize ?: existingAgeGroup.teamSize
        )

        if (updatedAgeGroup.startYear < updatedAgeGroup.endYear) {
            throw ValidationException("Start year cannot be before end year")
        }

        val teamSize = request.teamSize
        if (teamSize != null) {
            val club = clubRepository.getClubById(existingAgeGroup.clubId) ?: throw NotFoundException("Club not found")
            if (teamSize !in club.sport.teamSizes) {
                throw ValidationException("Team size $teamSize is not valid for ${club.sport}")
            }
        }

        val spanChanged = updatedAgeGroup.startYear != existingAgeGroup.startYear || updatedAgeGroup.endYear != existingAgeGroup.endYear
        if (spanChanged) {
            // Same uniqueness as creating: one age group per birth-year span per season.
            val clash = ageGroupRepository.findByClubAndSeason(existingAgeGroup.clubId, existingAgeGroup.seasonId)
                .any { it.id != ageGroupId && it.startYear == updatedAgeGroup.startYear && it.endYear == updatedAgeGroup.endYear }
            if (clash) {
                throw ConflictException("An age group for ${updatedAgeGroup.startYear}-${updatedAgeGroup.endYear} already exists in this season")
            }
            // Eligibility is `birthDate.year >= endYear`: raising endYear can shut out players already in the pool.
            if (updatedAgeGroup.endYear > existingAgeGroup.endYear) {
                val shutOut = playerRepository.getPlayersByAgeGroup(ageGroupId).count { it.birthDate.year < updatedAgeGroup.endYear }
                if (shutOut > 0) {
                    throw ConflictException("$shutOut player(s) in this age group were born before ${updatedAgeGroup.endYear}; remove them from the pool first")
                }
            }
        }

        ageGroupRepository.updateAgeGroup(updatedAgeGroup)
    }
}
