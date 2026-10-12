package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.OpponentDetails

/**
 * Opponent details (design 38f): the opponent, every match of the club against them with the scores of
 * those that started, and the teams and seasons the client needs for the head-to-head of any season.
 * Readable by any club member, like the Opponents list; [OpponentDetails.canEdit] follows `EDIT_OPPONENT`.
 */
class GetOpponentDetailsUseCase(
    private val opponentRepository: OpponentRepository,
    private val clubRepository: ClubRepository,
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val teamRepository: TeamRepository,
    private val seasonRepository: SeasonRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, opponentId: String): OpponentDetails = transactionManager.runInTransaction {
        val opponent = opponentRepository.getOpponentById(opponentId) ?: throw NotFoundException("Opponent not found")

        checkUserPermissionUseCase(userId, opponent.clubId, minClubRole = ClubRole.MEMBER)

        val canEdit = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = opponent.clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.EDIT_OPPONENT
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        val club = clubRepository.getClubById(opponent.clubId) ?: throw NotFoundException("Club not found")
        val matches = matchRepository.getMatchesByOpponent(opponentId)
        val started = matches.filter { it.status in STARTED }.map { it.id }
        val teamIds = matches.map { it.teamId }.toSet()

        OpponentDetails(
            opponent = opponent,
            clubName = club.name,
            matches = matches,
            scores = matchEventRepository.getScoresByMatches(started).values.toList(),
            teams = teamRepository.getTeamsByClub(opponent.clubId).filter { it.id in teamIds },
            seasons = seasonRepository.getSeasonsByClub(opponent.clubId),
            canEdit = canEdit
        )
    }

    private companion object {
        val STARTED = setOf(MatchStatus.LIVE, MatchStatus.HALF_TIME, MatchStatus.FINISHED)
    }
}
