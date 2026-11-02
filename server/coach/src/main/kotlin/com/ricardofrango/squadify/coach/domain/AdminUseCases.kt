package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.SpacePlanRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AdminSpace
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.UserStatus
import kotlin.time.Clock

/**
 * A platform admin: an active account marked so in the database, or whose email is in
 * `PLATFORM_ADMIN_EMAILS` ([adminEmails], CoachConfig). Only they set plans, until payments.
 */
class PlatformAdmins(private val userRepository: UserRepository, private val adminEmails: Set<String>) {
    suspend fun require(userId: String) {
        val user = userRepository.findById(userId)
        val admin = user != null && user.status == UserStatus.ACTIVE && (user.isPlatformAdmin || user.email.lowercase() in adminEmails)
        if (!admin) throw ForbiddenException("Only a platform admin can do this")
    }
}

/** Every space with its owner's email and its plan, for the platform admin. */
class GetAdminSpacesUseCase(
    private val admins: PlatformAdmins,
    private val plans: SpacePlanRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String): List<AdminSpace> = transactionManager.runInTransaction {
        admins.require(userId)
        plans.spaces().map { AdminSpace(it.id, it.name, it.ownerEmail, it.plan) }
    }
}

/** A platform admin sets a space's plan (REQUIREMENTS §5c); what's over the new plan's limits is locked, not deleted. */
class SetSpacePlanUseCase(
    private val admins: PlatformAdmins,
    private val plans: SpacePlanRepository,
    private val clubRepository: ClubRepository,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(userId: String, spaceId: String, plan: SpacePlan) = transactionManager.runInTransaction {
        admins.require(userId)
        clubRepository.getClubById(spaceId) ?: throw NotFoundException("Space not found")
        plans.setPlan(spaceId, plan, clock.now())
    }
}
