package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.birthYearsOverlap
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import kotlin.uuid.Uuid

class CreateAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val clubRepository: ClubRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: CreateAgeGroupsRequest): List<AgeGroup> = transactionManager.runInTransaction {
        accessPolicy.requireCreateAgeGroups(userId, request.clubId, "Only admins or coordinators can create age groups")

        val seasonId = request.seasonId ?: throw ValidationException("A season is required")
        if (request.items.isEmpty()) {
            throw ValidationException("At least one age group must be provided")
        }
        accessPolicy.requireRoomForAgeGroups(request.clubId, seasonId, request.items.size)

        val club = clubRepository.getClubById(request.clubId)
            ?: throw NotFoundException("Club not found")

        // Get existing age groups for this club and season to check for duplicates
        val existingAgeGroups = ageGroupRepository.findByClubAndSeason(request.clubId, seasonId)
        // Grows as items are accepted, so two items in this same request sharing a year are caught
        // too -- not just a clash with an age group already in the database. Any shared birth year
        // collides, not only an identical span (2011 vs 2011-2010 overlaps on 2011).
        val takenRanges = existingAgeGroups.map { it.startYear to it.endYear }.toMutableList()
        // Every clashing item is collected, not just the first, so the screen can mark them all at once
        // (ConflictException.rows, 0-based in request order). Within the batch, the later item of two
        // that share a year is the one reported.
        val conflictingRows = mutableListOf<Int>()

        val newAgeGroups = request.items.mapIndexed { index, item ->
            if (item.name.isBlank()) {
                throw ValidationException("Age group name cannot be empty")
            }
            if (item.startYear < item.endYear) {
                throw ValidationException("Start year cannot be before end year")
            }
            if (takenRanges.any { (start, end) -> birthYearsOverlap(item.startYear, item.endYear, start, end) }) {
                conflictingRows += index
            } else {
                takenRanges += item.startYear to item.endYear
            }
            if (item.teamSize != null && item.teamSize !in club.sport.teamSizes) {
                throw ValidationException("Team size ${item.teamSize} is not valid for ${club.sport}")
            }

            AgeGroup(
                id = Uuid.random().toString(),
                clubId = request.clubId,
                name = item.name,
                seasonId = seasonId,
                startYear = item.startYear,
                endYear = item.endYear,
                teamSize = item.teamSize
            )
        }

        if (conflictingRows.isNotEmpty()) {
            throw ConflictException(
                "Some birth years already have an age group in this season (items ${conflictingRows.joinToString { (it + 1).toString() }})",
                rows = conflictingRows
            )
        }

        ageGroupRepository.createAgeGroups(newAgeGroups)
    }
}
