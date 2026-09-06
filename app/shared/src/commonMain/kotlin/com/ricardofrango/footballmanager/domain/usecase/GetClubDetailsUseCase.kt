package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.*
import com.ricardofrango.footballmanager.model.*

data class ClubDetails(
    val club: Club,
    val teams: List<Team>,
    val players: List<Player>,
    val seasons: List<Season>,
    val ageGroups: List<AgeGroup>,
    val teamPlayers: List<TeamPlayer>,
    // Whether the current user is allowed to create a season/age group in this club
    // (CLUB_ADMIN/COORDINATOR/platform admin) -- lets the UI tell an admin "nothing here yet, create
    // one" apart from a plain member's "nothing here yet" when `seasons`/`ageGroups` is empty. See
    // PROCESS_FLOWS.md §3.3.
    val canEditClub: Boolean,
)

class GetClubDetailsUseCase(
    private val clubRepository: ClubRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val seasonRepository: SeasonRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val teamPlayerRepository: TeamPlayerRepository
) {
    suspend operator fun invoke(clubId: String): ClubDetails {
        val clubDetailsResponse = clubRepository.getClubById(clubId)
        val teams = teamRepository.getTeams(clubId)
        val players = playerRepository.getPlayers(clubId)
        val seasons = seasonRepository.getSeasons(clubId)
        val ageGroups = ageGroupRepository.getAgeGroups(clubId)
        val teamPlayers = teamPlayerRepository.getTeamPlayersByClub(clubId)

        return ClubDetails(
            club = clubDetailsResponse.club,
            teams = teams,
            players = players,
            seasons = seasons,
            ageGroups = ageGroups,
            teamPlayers = teamPlayers,
            canEditClub = clubDetailsResponse.canEditClub,
        )
    }
}
