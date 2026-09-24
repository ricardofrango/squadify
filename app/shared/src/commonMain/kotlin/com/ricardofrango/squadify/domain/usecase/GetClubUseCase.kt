package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository

/** A single club's own record, not the whole [GetClubDetailsUseCase] aggregate -- for screens that only
 * need the club itself (e.g. editing its details), not its teams/players/seasons/age groups too. */
class GetClubUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String) = clubRepository.getClubById(clubId).club
}
