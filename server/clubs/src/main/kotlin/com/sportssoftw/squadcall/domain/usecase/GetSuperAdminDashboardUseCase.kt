package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.model.SuperAdminDashboard

class GetSuperAdminDashboardUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val checkUserIsAdminUseCase: CheckUserIsAdminUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String): SuperAdminDashboard = transactionManager.runInTransaction {
        if (!checkUserIsAdminUseCase(userId)) {
            throw ForbiddenException("Only platform admins can access the super admin dashboard")
        }

        val totalClubs = clubRepository.countAllClubs()
        val totalUsers = userRepository.countAllUsers()
        val recentClubs = clubRepository.getRecentClubs(limit = 5)

        SuperAdminDashboard(
            totalClubs = totalClubs,
            totalUsers = totalUsers,
            activeSubscriptions = 0, // Placeholder
            recentClubs = recentClubs
        )
    }
}
