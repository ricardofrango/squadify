package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.*
import com.ricardofrango.footballmanager.model.*

data class ClubDetails(
    val club: Club,
    val teams: List<Team>,
    val players: List<Player>,
    val seasons: List<Season>,
    val ageGroups: List<AgeGroup>
)

class GetClubDetailsUseCase(
    private val clubRepository: ClubRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val seasonRepository: SeasonRepository,
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(clubId: String): ClubDetails {
        val club = clubRepository.getClubById(clubId)
        val teams = teamRepository.getTeams(clubId)
        val players = playerRepository.getPlayers(clubId)
        val seasons = seasonRepository.getSeasons(clubId)
        val ageGroups = ageGroupRepository.getAgeGroups(clubId)
        
        return ClubDetails(club, teams, players, seasons, ageGroups)
    }
}
