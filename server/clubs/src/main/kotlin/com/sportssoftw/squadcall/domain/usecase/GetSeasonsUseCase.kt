package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.Season

class GetSeasonsUseCase(
    private val seasonRepository: SeasonRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val userRepository: UserRepository,
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<Season> = transactionManager.runInTransaction {
        // 1. Check permissions first (Member of club, or a parent/player linked to someone in this club)
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

        // 2. Club admins/coordinators see every season. Everyone else -- plain member, coach/staff,
        //    player, parent -- only sees the season(s) of the age group(s) they're attached to (an
        //    AgeGroupStaff assignment or a linked player in that age group's pool).
        val user = userRepository.findById(userId)
        val userClubRole = userRepository.getUserRoleInClub(userId, clubId)
        val seesEverything = user?.isPlatformAdmin == true ||
            userClubRole == ClubRole.CLUB_ADMIN ||
            userClubRole == ClubRole.COORDINATOR

        if (seesEverything) {
            seasonRepository.getSeasonsByClub(clubId)
        } else {
            val visibleSeasonIds = visibleAgeGroups(playerRepository, ageGroupRepository, userId, clubId)
                .map { it.seasonId }.toSet()
            visibleSeasonIds.mapNotNull { seasonRepository.getSeasonById(it) }
        }
    }
}
