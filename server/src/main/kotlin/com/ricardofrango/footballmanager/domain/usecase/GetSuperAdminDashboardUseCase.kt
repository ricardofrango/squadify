package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.SuperAdminDashboard

class GetSuperAdminDashboardUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val checkUserIsAdminUseCase: CheckUserIsAdminUseCase
) {
    suspend operator fun invoke(userId: String): SuperAdminDashboard {
        if (!checkUserIsAdminUseCase(userId)) {
            throw ForbiddenException("Only platform admins can access the super admin dashboard")
        }

        val totalClubs = clubRepository.countAllClubs()
        val totalUsers = userRepository.countAllUsers()
        val recentClubs = clubRepository.getRecentClubs(limit = 5)

        return SuperAdminDashboard(
            totalClubs = totalClubs,
            totalUsers = totalUsers,
            activeSubscriptions = 0, // Placeholder
            recentClubs = recentClubs
        )
    }
}
