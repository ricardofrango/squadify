package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.AccessPolicy
import com.sportssoftw.squadify.domain.service.ClubMatchesBus
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.model.ClubMatchesUpdate
import kotlinx.coroutines.flow.Flow

class GetClubMatchesUpdatesUseCase(
    private val clubMatchesBus: ClubMatchesBus,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): Flow<ClubMatchesUpdate> = transactionManager.runInTransaction {
        // 1. Authorize: Member of club can see the club's live matches-list updates
        accessPolicy.requireMember(userId, clubId)
        accessPolicy.requireLiveInPlan(clubId)

        // 2. Return the Reactive Stream
        clubMatchesBus.subscribe(clubId)
    }
}
