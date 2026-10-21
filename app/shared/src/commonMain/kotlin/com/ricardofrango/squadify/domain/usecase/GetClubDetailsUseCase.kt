package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.*
import com.ricardofrango.squadify.model.*

data class ClubDetails(
    val club: Club,
    val teams: List<Team>,
    val players: List<Player>,
    val seasons: List<Season>,
    val ageGroups: List<AgeGroup>,
    // Which age-group pool(s) each player belongs to. A player can be in several; teams within an age
    // group all draw from the same pool.
    val ageGroupPlayers: List<AgeGroupPlayer>,
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
)

class GetClubDetailsUseCase(
    private val clubRepository: ClubRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val seasonRepository: SeasonRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val ageGroupPlayerRepository: AgeGroupPlayerRepository
) {
    suspend operator fun invoke(clubId: String): ClubDetails {
        val clubDetailsResponse = clubRepository.getClubById(clubId)
        val teams = teamRepository.getTeams(clubId)
        val players = playerRepository.getPlayers(clubId)
        val seasons = seasonRepository.getSeasons(clubId)
        val ageGroups = ageGroupRepository.getAgeGroups(clubId)
        val ageGroupPlayers = ageGroupPlayerRepository.getAgeGroupPlayersByClub(clubId)

        return ClubDetails(
            club = clubDetailsResponse.club,
            teams = teams,
            players = players,
            seasons = seasons,
            ageGroups = ageGroups,
            ageGroupPlayers = ageGroupPlayers,
            canEditClub = clubDetailsResponse.canEditClub,
            viewerRole = clubDetailsResponse.viewerRole,
            viewerAgeGroups = clubDetailsResponse.viewerAgeGroups,
            createTeamAgeGroupIds = clubDetailsResponse.createTeamAgeGroupIds,
            createPlayersAgeGroupIds = clubDetailsResponse.createPlayersAgeGroupIds,
            canManagePlayerStatus = clubDetailsResponse.canManagePlayerStatus,
        )
    }
}
