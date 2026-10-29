package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.SuperAdminDashboard

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
