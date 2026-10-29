package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import kotlinx.coroutines.flow.Flow

class GetClubMatchesUpdatesUseCase(
    private val clubMatchesBus: ClubMatchesBus,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): Flow<ClubMatchesUpdate> = transactionManager.runInTransaction {
        // 1. Authorize: Member of club can see the club's live matches-list updates
        accessPolicy.requireMember(userId, clubId)

        // 2. Return the Reactive Stream
        clubMatchesBus.subscribe(clubId)
    }
}
