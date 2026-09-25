package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository

/** A single club's own record plus [ClubDetailsResponse.canEditClub] -- not the whole
 * [GetClubDetailsUseCase] aggregate, for screens that only need the club itself (e.g. editing its
 * details), not its teams/players/seasons/age groups too. `canEditClub` is exposed (not just `.club`
 * discarded) so a caller like `EditClubViewModel` can refuse to show editable content to a non-editor
 * who reached the screen some other way than its own gated entry point (e.g. a deep link). */
class GetClubUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String) = clubRepository.getClubById(clubId)
}
