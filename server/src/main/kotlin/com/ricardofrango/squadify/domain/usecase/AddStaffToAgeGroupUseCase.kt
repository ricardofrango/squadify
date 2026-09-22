package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.*
import kotlin.uuid.Uuid

class AddStaffToAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val transactionManager: TransactionManager,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val emailService: EmailService,
    private val pushNotificationService: PushNotificationService
) {
    suspend operator fun invoke(
        userId: String,
        ageGroupId: String,
        request: AddStaffToAgeGroupRequest
    ): Boolean {
        val staffAdded = mutableListOf<User>()
        var ageGroupName = ""
        var clubName = "your club"
        var pushTokensByUser: Map<String, List<String>> = emptyMap()

        val result = transactionManager.runInTransaction {
            val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
                ?: throw NotFoundException("Age group not found")
            ageGroupName = ageGroup.name
            // clubRepository has no ambient transaction of its own outside this block -- fetched here,
            // not after the transaction closes, even though it's only used for the emails sent below.
            clubRepository.getClubById(ageGroup.clubId)?.let { clubName = it.name }

            checkUserPermissionUseCase(
                userId = userId,
                clubId = ageGroup.clubId,
                minClubRole = ClubRole.COORDINATOR,
                errorMessage = "Only admins or coordinators can add staff to age groups"
            )

            val staffUserIds = request.staff.map { it.userId }
            val userRolesMap = userRepository.getUserRolesInClub(staffUserIds, ageGroup.clubId)

            val staffToCreate = request.staff.map { staffItem ->
                userRolesMap[staffItem.userId]
                    ?: throw ForbiddenException("User ${staffItem.userId} must be a member of the club before being added to an age group")

                userRepository.findById(staffItem.userId)?.let { staffAdded.add(it) }

                AgeGroupStaff(
                    id = Uuid.random().toString(),
                    ageGroupId = ageGroupId,
                    userId = staffItem.userId,
                    role = staffItem.role
                )
            }

            val added = ageGroupRepository.addStaffToAgeGroup(staffToCreate)

            // fcmTokenRepository has no ambient transaction of its own outside this block either --
            // fetched here for the same reason clubName is, even though it's only used for the push
            // notifications sent below.
            if (staffAdded.isNotEmpty()) {
                pushTokensByUser = fcmTokenRepository.getTokensForUsers(staffAdded.map { it.id })
            }

            added
        }

        // Sent after the transaction commits -- a failed/slow email or push must never roll back the
        // assignment.
        if (staffAdded.isNotEmpty()) {
            staffAdded.forEach {
                emailService.sendAgeGroupStaffAdded(to = it.email, firstName = it.firstName, ageGroupName = ageGroupName, clubName = clubName)
                pushTokensByUser[it.id]?.let { tokens ->
                    pushNotificationService.sendAgeGroupStaffAdded(tokens = tokens, ageGroupName = ageGroupName, clubName = clubName)
                }
            }
        }

        return result
    }
}
