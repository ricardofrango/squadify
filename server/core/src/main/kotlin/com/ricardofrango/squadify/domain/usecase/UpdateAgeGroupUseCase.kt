package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.UpdateAgeGroupRequest
import com.ricardofrango.squadify.model.birthYearsOverlap

/**
 * Edit an age group: name, birth-year span and default team size. The same rules as creating one
 * (CreateAgeGroupsUseCase) apply, plus one of its own: the new span can't leave a player already in
 * the pool ineligible (born before the new oldest year) -- they'd silently stop being allowed to play.
 */
class UpdateAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
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

        accessPolicy.requireEditAgeGroup(
            userId,
            GameScope(existingAgeGroup.clubId, ageGroupId = ageGroupId),
            "Only admins or coordinators can update age groups"
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
            // Same rule as creating: no birth year in two age groups of the same season -- any overlap
            // collides, not only an identical span.
            val clash = ageGroupRepository.findByClubAndSeason(existingAgeGroup.clubId, existingAgeGroup.seasonId)
                .any { it.id != ageGroupId && birthYearsOverlap(updatedAgeGroup.startYear, updatedAgeGroup.endYear, it.startYear, it.endYear) }
            if (clash) {
                throw ConflictException("Another age group already covers some of ${updatedAgeGroup.startYear}-${updatedAgeGroup.endYear} in this season")
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
