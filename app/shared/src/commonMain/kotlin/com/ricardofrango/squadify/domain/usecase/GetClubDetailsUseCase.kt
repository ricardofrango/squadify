package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.*
import com.ricardofrango.squadify.model.*

data class ClubDetails(
    val club: Club,
    val teams: List<Team>,
    val seasons: List<Season>,
    val ageGroups: List<AgeGroup>,
    // Whether the current user is allowed to create a season/age group in this club
    // (CLUB_ADMIN/COORDINATOR/platform admin) -- lets the UI tell an admin "nothing here yet, create
    // one" apart from a plain member's "nothing here yet" when `seasons`/`ageGroups` is empty. See
    // PROCESS_FLOWS.md §3.3.
    val canEditClub: Boolean,
    // The viewer, for the "More" panel (design 37) -- see ClubDetailsResponse.
    val viewerRole: ClubRole? = null,
    val viewerAgeGroups: List<ViewerAgeGroup> = emptyList(),
    val createTeamAgeGroupIds: List<String> = emptyList(),
    val createPlayersAgeGroupIds: List<String> = emptyList(),
    // May disable and reactivate players (the club-level MANAGE_PLAYER_STATUS setting).
    val canManagePlayerStatus: Boolean = false,
    // How many players each age group's pool holds, by age group id (the players themselves load a
    // page at a time, on the Players screen).
    val ageGroupPlayerCounts: Map<String, Int> = emptyMap(),
)

class GetClubDetailsUseCase(
    private val clubRepository: ClubRepository,
    private val teamRepository: TeamRepository,
    private val seasonRepository: SeasonRepository,
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(clubId: String): ClubDetails {
        val clubDetailsResponse = clubRepository.getClubById(clubId)
        val teams = teamRepository.getTeams(clubId)
        val seasons = seasonRepository.getSeasons(clubId)
        val ageGroups = ageGroupRepository.getAgeGroups(clubId)

        return ClubDetails(
            club = clubDetailsResponse.club,
            teams = teams,
            seasons = seasons,
            ageGroups = ageGroups,
            canEditClub = clubDetailsResponse.canEditClub,
            viewerRole = clubDetailsResponse.viewerRole,
            viewerAgeGroups = clubDetailsResponse.viewerAgeGroups,
            createTeamAgeGroupIds = clubDetailsResponse.createTeamAgeGroupIds,
            createPlayersAgeGroupIds = clubDetailsResponse.createPlayersAgeGroupIds,
            canManagePlayerStatus = clubDetailsResponse.canManagePlayerStatus,
            ageGroupPlayerCounts = clubDetailsResponse.ageGroupPlayerCounts,
        )
    }
}
