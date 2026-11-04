package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.scope
import com.ricardofrango.squadify.domain.access.allowed
import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.matchTallies
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchReportStatus
import com.ricardofrango.squadify.model.MatchReportSummary
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.domain.stats.MatchPlayerStatsCalculator

class GetMatchDetailsUseCase(
    private val matchRepository: MatchRepository,
    private val opponentRepository: OpponentRepository,
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val playerRepository: PlayerRepository,
    private val matchEventRepository: MatchEventRepository,
    private val matchReportRepository: MatchReportRepository,
    private val matchPlayerStatsRepository: MatchPlayerStatsRepository,
    private val userRepository: UserRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(userId: String, matchId: String): MatchDetails = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        accessPolicy.requireViewGames(userId, match.scope)

        val opponent = opponentRepository.getOpponentById(match.opponentId)
            ?: throw NotFoundException("Opponent not found")
        val team = teamRepository.getTeamById(match.teamId)
            ?: throw NotFoundException("Team not found")
        val ageGroup = ageGroupRepository.getAgeGroupById(team.ageGroupId)
            ?: throw NotFoundException("Age group not found")
        val season = seasonRepository.getSeasonById(team.seasonId)
            ?: throw NotFoundException("Season not found")

        val callUps = matchRepository.getCallUpsForMatch(matchId)
        val calledUpIds = callUps.map { it.player.id }.toSet()
        // The age group's pool minus whoever is already in the call-up -- the "Available" list. A
        // disabled player stays in the pool but can't be called up.
        val poolPlayers = playerRepository.getPlayersByAgeGroup(team.ageGroupId)
            .filter { it.id !in calledUpIds && it.active }

        // Each flag is a non-throwing probe of the exact same configurable-permission gate its
        // corresponding use case actually enforces (UpdateMatchUseCase/DeleteMatchUseCase,
        // ManageCallUpsUseCase, and the live-match use cases respectively) -- resolved independently
        // rather than one implying another, since a club can now override each of the three away from
        // its default and have them disagree (e.g. call-ups open to staff but live-match ops
        // coordinator-only). See TECHNICAL_DECISIONS.md §35 on why these must track the real
        // configurable thresholds instead of a literal minAgeGroupRole.
        val canManageMatch = allowed { accessPolicy.requireManageMatch(userId, match.scope) }

        // The report card: only for a finished match, and only for someone who may read the report
        // (same gate as GetMatchReportUseCase -- coordinators/admins and the age group's staff).
        val canReadReport = match.status == MatchStatus.FINISHED && allowed { accessPolicy.requireSeeRatings(userId, match.scope) }
        val saved = if (canReadReport) matchReportRepository.getByMatch(matchId) else null
        val stats = if (canReadReport) matchPlayerStatsRepository.getByMatch(matchId) else emptyList()
        val report = if (canReadReport) {
            MatchReportSummary(
                status = when {
                    saved == null -> MatchReportStatus.NOT_STARTED
                    saved.completedAt != null -> MatchReportStatus.COMPLETED
                    else -> MatchReportStatus.DRAFT
                },
                updatedAt = saved?.updatedAt,
                updatedByName = saved?.let { userRepository.findById(it.updatedByUserId) }?.let { "${it.firstName} ${it.lastName}" },
                ratedPlayers = stats.count { it.rating != null },
                ratablePlayers = stats.size,
                canEdit = canManageMatch
            )
        } else null

        // A finished match's record (§66): all of it for the report's readers, the goals for anyone else.
        val finished = match.status == MatchStatus.FINISHED
        val allEvents = if (finished) matchEventRepository.getEventsByMatch(matchId) else emptyList()
        val goalIds = allEvents.filter { it.type == EventType.GOAL || it.type == EventType.OWN_GOAL }.map { it.id }.toSet()
        val events = if (canReadReport) allEvents else allEvents.filter { it.id in goalIds || (it.type == EventType.ASSIST && it.relatedEventId in goalIds) }
        val playerStats = if (!canReadReport) emptyList() else {
            // A match finished before post-match stats existed has none stored: worked out the same way now.
            val lines = stats.ifEmpty { MatchPlayerStatsCalculator.calculate(match, callUps.map { it.callUp }, allEvents) }
            // Ratings count once the report is completed, as everywhere else.
            if (saved?.completedAt != null) lines else lines.map { it.copy(rating = null) }
        }
        val lineupChanges = if (canReadReport) matchRepository.getLineupChanges(matchId) else emptyList()

        val club = clubRepository.getClubById(match.clubId)
        MatchDetails(
            match = match,
            opponent = opponent,
            team = team,
            ageGroup = ageGroup,
            season = season,
            callUps = callUps,
            poolPlayers = poolPlayers,
            canManageMatch = canManageMatch,
            canManageCallUps = allowed { accessPolicy.requireManageCallUps(userId, match.scope) },
            canManageLiveMatch = allowed { accessPolicy.requireManageLiveMatch(userId, match.scope) },
            score = matchEventRepository.getScoreByMatch(matchId),
            report = report,
            serverNow = kotlin.time.Clock.System.now(),
            clubLogoUrl = club?.logoUrl,
            clubLocation = club?.let { it.stadiumLocation ?: it.address },
            canSeeMatchRecord = canReadReport,
            events = events,
            tallies = if (finished) matchTallies(allEvents) else null,
            playerStats = playerStats,
            lineupChanges = lineupChanges,
            teamSizeOptions = club?.sport?.teamSizes.orEmpty(),
            // The players' lines stay (editing the record afterwards needs them); the Stats tab shows them only on a plan with stats.
            statsInPlan = allowed { accessPolicy.requireStatsInPlan(match.clubId) }
        )
    }
}
