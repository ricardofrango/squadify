package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubMatchesUpdate

/**
 * Permanently removes a team from its age group, with every match it has -- played ones too -- and
 * each match's events, call-ups, player stats and report. Behind the same check as creating a team
 * ([AccessPolicy.requireManageTeams], in the age group's context): whoever can add teams to the age
 * group can remove them. The players stay: they belong to the age group's pool, not to the team.
 */
class DeleteTeamUseCase(
    private val teamRepository: TeamRepository,
    private val matchRepository: MatchRepository,
    private val accessPolicy: AccessPolicy,
    private val clubMatchesBus: ClubMatchesBus,
    private val transactionManager: TransactionManager
) {
    /** Returns how many matches were deleted with the team. */
    suspend operator fun invoke(userId: String, teamId: String): Int {
        var clubId = ""
        var matchIds: List<String> = emptyList()

        transactionManager.runInTransaction {
            val team = teamRepository.getTeamById(teamId) ?: throw NotFoundException("Team not found")
            clubId = team.clubId

            accessPolicy.requireManageTeams(
                userId,
                GameScope(team.clubId, ageGroupId = team.ageGroupId),
                "Only admins, coordinators, or this age group's coaches can delete teams"
            )

            // Read before the delete, only to tell open screens which matches went.
            matchIds = matchRepository.getMatchesByTeam(teamId).map { it.id }
            // ON DELETE CASCADE (V21) takes the matches and everything under them.
            teamRepository.deleteTeam(teamId)
        }

        // After the commit, as in DeleteMatchUseCase: a failed broadcast must never roll back the delete.
        matchIds.forEach { clubMatchesBus.publish(clubId, ClubMatchesUpdate(deletedMatchId = it)) }
        return matchIds.size
    }
}
