package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchListStatus
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchesPage
import com.ricardofrango.squadify.model.Team
import kotlin.time.Clock

class GetMatchesUseCase(
    private val matchRepository: MatchRepository,
    private val teamRepository: TeamRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val matchEventRepository: MatchEventRepository
) {
    suspend operator fun invoke(userId: String, clubId: String, teamId: String? = null): List<Match> = transactionManager.runInTransaction {
        if (teamId != null) {
            // Scoped to one team: whoever sees that team's games (on the clubs server, a club
            // admin/coordinator, its age group's staff, or a player/parent linked to it).
            accessPolicy.requireViewGames(userId, GameScope(clubId, teamId = teamId))

            matchRepository.getMatchesByTeam(teamId)
        } else {
            // No team filter: the matches of the teams in the age groups the caller sees -- every match for
            // club admins and coordinators (the coach's owner), only their own corner of the club for anyone
            // else. See "issues to fix.md" -- "A plain club member sees the whole club, not just 'the club.'"
            accessPolicy.requireViewClub(userId, clubId)

            val visible = accessPolicy.visibleAgeGroupIds(userId, clubId)
            if (visible == null) {
                matchRepository.getMatchesByClub(clubId)
            } else {
                visibleTeams(clubId, visible).flatMap { matchRepository.getMatchesByTeam(it.id) }.distinctBy { it.id }
            }
        }
    }

    /**
     * One page of the club Matches tab (`GET /matches` with `limit`): [status]'s list, narrowed to
     * [seasonId], [ageGroupId] and [teamId] here rather than on the device, with the score of every
     * started match on the page. Narrowed to the teams of the age groups the caller sees, as in
     * [invoke]; a team outside them gives an empty page.
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
        accessPolicy.requireViewClub(userId, clubId)

        val visible = visibleTeams(clubId, accessPolicy.visibleAgeGroupIds(userId, clubId))
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

    // The teams of the age groups the caller sees (AccessPolicy.visibleAgeGroupIds), every team for null.
    private suspend fun visibleTeams(clubId: String, visibleAgeGroupIds: Set<String>?): List<Team> =
        teamRepository.getTeamsByClub(clubId).filter { visibleAgeGroupIds == null || it.ageGroupId in visibleAgeGroupIds }
}
