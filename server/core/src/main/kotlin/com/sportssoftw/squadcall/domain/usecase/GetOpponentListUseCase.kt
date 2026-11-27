package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.GameScope
import com.sportssoftw.squadcall.domain.access.allowed
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.Opponent
import com.sportssoftw.squadcall.model.OpponentList
import com.sportssoftw.squadcall.model.SearchText

/**
 * The Opponents screen (design 38): the club's opponents by name and whether the caller may create one.
 * Who may read it is the same as [GetOpponentsUseCase], including the [ageGroupId] path for a coach
 * picking an opponent for their own age group's match.
 *
 * With [limit], one page of it, searched on the server ([search] on the name or short name, ignoring
 * case and accents); without, the whole list (the form's duplicate-name check, and older app builds).
 */
class GetOpponentListUseCase(
    private val opponentRepository: OpponentRepository,
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        ageGroupId: String? = null,
        search: String? = null,
        limit: Int? = null,
        offset: Int = 0
    ): OpponentList = transactionManager.runInTransaction {
        ageGroupId?.let { id ->
            val ageGroup = ageGroupRepository.getAgeGroupById(id)
                ?: throw NotFoundException("Age group not found")
            if (ageGroup.clubId != clubId) {
                throw ForbiddenException("Age group does not belong to this club")
            }
        }

        accessPolicy.requireViewOpponents(userId, GameScope(clubId, ageGroupId = ageGroupId))

        val club = clubRepository.getClubById(clubId) ?: throw NotFoundException("Club not found")
        val key = search?.trim()?.let(SearchText::key)?.takeIf { it.isNotEmpty() }
        val opponents: List<Opponent>
        val hasMore: Boolean
        if (limit == null) {
            opponents = opponentRepository.getOpponentsByClub(clubId).sortedBy { SearchText.key(it.name) }
            hasMore = false
        } else {
            // One row past the page says whether there's another.
            val rows = opponentRepository.getOpponentsPage(clubId, key, limit + 1, offset)
            opponents = rows.take(limit)
            hasMore = rows.size > limit
        }
        // A row on the page, unsearched, already answers it.
        val anyOpponents = (key == null && opponents.isNotEmpty()) || opponentRepository.hasOpponents(clubId)

        val canCreate = allowed { accessPolicy.requireCreateOpponent(userId, GameScope(clubId, ageGroupId = ageGroupId)) }

        OpponentList(
            clubName = club.name,
            opponents = opponents,
            canCreate = canCreate,
            hasMore = hasMore,
            anyOpponents = anyOpponents
        )
    }
}
