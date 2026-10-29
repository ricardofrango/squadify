package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerListFilter
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayersPage
import com.ricardofrango.squadify.model.SearchText
import com.ricardofrango.squadify.model.AgeGroupRole

/**
 * The players a coordinator or an age-group coach may pick from when adding to this age group: every
 * club player old enough for it (`birthDate.year >= ageGroup.endYear`) who isn't already in it.
 * [invoke] returns them all (older app builds); [page] one page by name, searched on the server.
 */
class GetEligiblePlayersForAgeGroupUseCase(
    private val playerRepository: PlayerRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, ageGroupId: String): List<Player> = transactionManager.runInTransaction {
        val ageGroup = authorizedAgeGroup(userId, ageGroupId)
        val alreadyIn = playerRepository.getPlayersByAgeGroup(ageGroupId).map { it.id }.toSet()
        playerRepository.getPlayersByClub(ageGroup.clubId)
            .filter { it.active && it.birthDate.year >= ageGroup.endYear && it.id !in alreadyIn }
    }

    suspend fun page(userId: String, ageGroupId: String, search: String?, limit: Int, offset: Int): PlayersPage =
        transactionManager.runInTransaction {
            val ageGroup = authorizedAgeGroup(userId, ageGroupId)
            val eligible = PlayerListFilter(notInAgeGroupId = ageGroupId, bornFromYear = ageGroup.endYear, activeOnly = true)
            val key = search?.trim()?.let(SearchText::key)?.takeIf { it.isNotEmpty() }
            // One extra row says whether another page follows.
            val rows = playerRepository.getPlayersPage(ageGroup.clubId, eligible.copy(search = key), limit + 1, offset)
            PlayersPage(
                players = rows.take(limit),
                hasMore = rows.size > limit,
                // An unsearched row already answers it.
                anyPlayers = (key == null && rows.isNotEmpty()) || playerRepository.hasPlayers(ageGroup.clubId, eligible)
            )
        }

    private suspend fun authorizedAgeGroup(userId: String, ageGroupId: String) =
        (ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")).also {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = it.clubId,
                minClubRole = ClubRole.MEMBER,
                minAgeGroupRole = AgeGroupRole.STAFF,
                ageGroupId = ageGroupId
            )
        }
}
