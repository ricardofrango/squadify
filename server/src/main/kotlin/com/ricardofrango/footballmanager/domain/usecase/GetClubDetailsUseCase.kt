package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.Club
import com.ricardofrango.footballmanager.model.Role

class GetClubDetailsUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, clubId: String): Club {
        val user = userRepository.findById(userId) ?: throw ForbiddenException("User not found")
        
        // Platform Admins can see everything
        if (user.isPlatformAdmin) {
            return clubRepository.getClubById(clubId) ?: throw ForbiddenException("Club not found")
        }

        // For regular users, check if they are a member of the club
        val userRole = userRepository.getUserRoleInClub(userId, clubId)
            ?: throw ForbiddenException("You do not have access to this club")

        return clubRepository.getClubById(clubId) ?: throw ForbiddenException("Club not found")
    }
}
