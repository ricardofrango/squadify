package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchListStatus
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchesPage
import com.ricardofrango.squadify.model.Team
import kotlin.time.Clock

class GetMatchesUseCase(
    private val matchRepository: MatchRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val playerRepository: PlayerRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val matchEventRepository: MatchEventRepository
) {
    suspend operator fun invoke(userId: String, clubId: String, teamId: String? = null): List<Match> = transactionManager.runInTransaction {
        if (teamId != null) {
            // Scoped to one team: a club admin/coordinator, that team's coach/staff, or a player/parent
            // linked to that team can see its match calendar.
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.MEMBER,
                minAgeGroupRole = AgeGroupRole.STAFF,
                teamId = teamId,
                allowPlayerOrParentAccess = true
            )

            matchRepository.getMatchesByTeam(teamId)
        } else {
            // No team filter: club admins/coordinators see every match in the club. Everyone else --
            // plain member, coach/staff, player, parent -- only sees matches for teams they actually have
            // access to (a AgeGroupRole assignment or a linked player via TeamPlayers/UserPlayers). See
            // "issues to fix.md" -- "A plain club member sees the whole club, not just 'the club.'"
            checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

            if (seesEverything(userId, clubId)) {
                matchRepository.getMatchesByClub(clubId)
            } else {
                visibleTeams(userId, clubId).flatMap { matchRepository.getMatchesByTeam(it.id) }.distinctBy { it.id }
            }
        }
    }

    /**
     * One page of the club Matches tab (`GET /matches` with `limit`): [status]'s list, narrowed to
     * [seasonId], [ageGroupId] and [teamId] here rather than on the device, with the score of every
     * started match on the page. Below coordinator, only the teams of the viewer's own age groups, as
     * in [invoke]; a team outside them gives an empty page.
     */
    suspend fun page(
        userId: String,
        clubId: String,
        status: MatchListStatus,
        seasonId: String?,
        ageGroupId: String?,
        teamId: String?,
        limit: Int,
        offset: Int
    ): MatchesPage = transactionManager.runInTransaction {
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

        val visible = if (seesEverything(userId, clubId)) teamRepository.getTeamsByClub(clubId) else visibleTeams(userId, clubId)
        val teamIds = visible
            .filter { (seasonId == null || it.seasonId == seasonId) && (ageGroupId == null || it.ageGroupId == ageGroupId) && (teamId == null || it.id == teamId) }
            .map { it.id }
        // One extra row says whether another page follows.
        val now = Clock.System.now()
        val rows = matchRepository.getMatchesPage(teamIds, status, limit + 1, offset, now)
        val matches = rows.take(limit)
        val started = matches.filter { it.status == MatchStatus.FINISHED || it.status == MatchStatus.LIVE || it.status == MatchStatus.HALF_TIME }
        MatchesPage(
            matches = matches,
            scores = if (started.isEmpty()) emptyList() else matchEventRepository.getScoresByMatches(started.map { it.id }).values.toList(),
            hasMore = rows.size > limit,
            // A short first page is the whole list; otherwise count it.
            total = if (offset == 0 && rows.size <= limit) rows.size else matchRepository.countMatches(teamIds, status, now),
            anyMatches = rows.isNotEmpty() || matchRepository.hasMatches(visible.map { it.id })
        )
    }

    // Club admins, coordinators and platform admins see every match of the club.
    private suspend fun seesEverything(userId: String, clubId: String): Boolean {
        val role = userRepository.getUserRoleInClub(userId, clubId)
        return role == ClubRole.CLUB_ADMIN || role == ClubRole.COORDINATOR || userRepository.findById(userId)?.isPlatformAdmin == true
    }

    // Everyone else: the teams of the age groups they're staff on or have a linked player in.
    private suspend fun visibleTeams(userId: String, clubId: String): List<Team> {
        val visibleAgeGroupIds = visibleAgeGroups(playerRepository, ageGroupRepository, userId, clubId).map { it.id }.toSet()
        return teamRepository.getTeamsByClub(clubId).filter { it.ageGroupId in visibleAgeGroupIds }
    }
}
