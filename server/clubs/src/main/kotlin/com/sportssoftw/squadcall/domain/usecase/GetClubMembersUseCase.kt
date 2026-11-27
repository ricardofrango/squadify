package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.model.ClubMemberSummary
import com.sportssoftw.squadcall.model.ClubRole

/**
 * Who a coordinator can pick from when assigning an age group's coaches/staff -- staff must already
 * be a club member (see AddStaffToAgeGroupUseCase), so this is the picker's source list.
 */
class GetClubMembersUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<ClubMemberSummary> = transactionManager.runInTransaction {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can view club members"
        )

        clubRepository.getClubMembers(clubId)
    }
}
