package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.StaffInviteRepository
import com.ricardofrango.squadify.coach.data.StoredStaffInvite
import com.ricardofrango.squadify.coach.data.newInviteId
import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.AgeGroupStaff
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.StaffInvite
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/**
 * Invite codes (TECHNICAL_DECISIONS §72): six characters from an alphabet without the ones easily
 * misread (0/O, 1/I/L), shown as "K7Q-4MZ", typed in any case, with or without the dash.
 */
object InviteCodes {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val LENGTH = 6
    private val random = SecureRandom()

    fun generate(): String = (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** What the coach typed, as stored: upper case, letters and digits only. */
    fun normalize(typed: String): String = typed.uppercase().filter { it.isLetterOrDigit() }

    /** As shown and shared: "K7Q-4MZ". */
    fun display(code: String): String = if (code.length == LENGTH) "${code.take(3)}-${code.drop(3)}" else code
}

private fun StoredStaffInvite.toModel(ageGroup: AgeGroup) =
    StaffInvite(InviteCodes.display(code), ageGroup.id, ageGroup.name, createdAt, expiresAt)

private suspend fun AgeGroupRepository.ageGroupOrNotFound(id: String) =
    getAgeGroupById(id) ?: throw NotFoundException("Age group not found")

/** The owner creates a code for one age group, valid for [validity]. */
class CreateStaffInviteUseCase(
    private val invites: StaffInviteRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System,
    private val validity: Duration = 7.days
) {
    suspend operator fun invoke(userId: String, ageGroupId: String): StaffInvite = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.ageGroupOrNotFound(ageGroupId)
        accessPolicy.requireManageStaff(userId, GameScope(ageGroup.clubId, ageGroupId = ageGroupId))
        // 31^6 codes: a clash is rare, but never reuse one.
        val code = generateSequence { InviteCodes.generate() }.take(10).firstOrNull { invites.findByCode(it) == null }
            ?: throw ConflictException("Couldn't create an invite code, try again")
        val now = clock.now()
        val invite = StoredStaffInvite(newInviteId(), code, ageGroupId, userId, now, now + validity, acceptedBy = null)
        invites.create(invite)
        invite.toModel(ageGroup)
    }
}

/** The owner's codes for an age group that are still waiting to be used. */
class GetStaffInvitesUseCase(
    private val invites: StaffInviteRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(userId: String, ageGroupId: String): List<StaffInvite> = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.ageGroupOrNotFound(ageGroupId)
        accessPolicy.requireManageStaff(userId, GameScope(ageGroup.clubId, ageGroupId = ageGroupId))
        invites.pending(ageGroupId, clock.now()).map { it.toModel(ageGroup) }
    }
}

/** The owner withdraws a code that hasn't been used. */
class RevokeStaffInviteUseCase(
    private val invites: StaffInviteRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, ageGroupId: String, code: String) = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.ageGroupOrNotFound(ageGroupId)
        accessPolicy.requireManageStaff(userId, GameScope(ageGroup.clubId, ageGroupId = ageGroupId))
        if (!invites.revoke(ageGroupId, InviteCodes.normalize(code))) throw NotFoundException("Invite not found")
    }
}

/**
 * Someone signed in uses a code: they join the space (if not in it yet) and become staff on the
 * code's age group. One space per account for now, so a member of another space is refused.
 */
class AcceptStaffInviteUseCase(
    private val invites: StaffInviteRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val clubRepository: ClubRepository,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(userId: String, typedCode: String): CoachSpace = transactionManager.runInTransaction {
        val invite = invites.findByCode(InviteCodes.normalize(typedCode)) ?: throw NotFoundException("Invite code not found")
        if (invite.acceptedBy != null) throw ConflictException("This invite code has already been used")
        val now = clock.now()
        if (invite.expiresAt <= now) throw ValidationException("This invite code has expired, ask for a new one")
        val ageGroup = ageGroupRepository.ageGroupOrNotFound(invite.ageGroupId)
        val spaceId = ageGroup.clubId

        val memberships = clubRepository.getMemberships(userId)
        if (memberships.any { it.first.id != spaceId }) throw ConflictException("You already belong to another space")
        val role = memberships.firstOrNull()?.second
        if (role == ClubRole.CLUB_ADMIN) throw ConflictException("You own this space, so you're already on every age group")
        if (ageGroupRepository.getUserRoleInAgeGroup(userId, ageGroup.id) != null) throw ConflictException("You're already staff on ${ageGroup.name}")

        if (role == null) clubRepository.addMember(spaceId, userId, ClubRole.MEMBER)
        ageGroupRepository.addStaffToAgeGroup(listOf(AgeGroupStaff(Uuid.random().toString(), ageGroup.id, userId, AgeGroupRole.COACH)))
        // Single-use: a second, simultaneous use loses here and rolls back.
        if (!invites.markAccepted(invite.id, userId, now)) throw ConflictException("This invite code has already been used")

        val space = memberships.firstOrNull()?.first ?: clubRepository.getClubById(spaceId) ?: throw NotFoundException("Space not found")
        CoachSpace(space, isOwner = false)
    }
}

/**
 * The owner takes someone off an age group. Off their last age group of the space, they leave the
 * space too. The owner can't be removed.
 */
class RemoveStaffUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val clubRepository: ClubRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, ageGroupId: String, staffUserId: String) = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.ageGroupOrNotFound(ageGroupId)
        accessPolicy.requireManageStaff(userId, GameScope(ageGroup.clubId, ageGroupId = ageGroupId))
        if (clubRepository.getMemberships(staffUserId).any { it.first.id == ageGroup.clubId && it.second == ClubRole.CLUB_ADMIN }) {
            throw ValidationException("The owner can't be removed")
        }
        if (!ageGroupRepository.removeStaffFromAgeGroup(ageGroupId, staffUserId)) throw NotFoundException("Not staff on this age group")
        val stillOn = ageGroupRepository.getAgeGroupsByClub(ageGroup.clubId).any { ageGroupRepository.getUserRoleInAgeGroup(staffUserId, it.id) != null }
        if (!stillOn) clubRepository.removeMember(ageGroup.clubId, staffUserId)
    }
}
