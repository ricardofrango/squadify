package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.model.ClubRole
import kotlinx.coroutines.flow.Flow

class GetClubMatchesUpdatesUseCase(
    private val clubMatchesBus: ClubMatchesBus,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): Flow<ClubMatchesUpdate> = transactionManager.runInTransaction {
        // 1. Authorize: Member of club can see the club's live matches-list updates
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.MEMBER
        )

        // 2. Return the Reactive Stream
        clubMatchesBus.subscribe(clubId)
    }
}
