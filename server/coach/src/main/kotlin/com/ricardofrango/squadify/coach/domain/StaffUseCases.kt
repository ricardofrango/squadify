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
import com.ricardofrango.squadify.model.CreateStaffInviteRequest
import com.ricardofrango.squadify.model.StaffInvite
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.model.PushLink
import kotlin.time.Instant
import org.slf4j.LoggerFactory
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
    StaffInvite(InviteCodes.display(code), ageGroup.id, ageGroup.name, createdAt, expiresAt, email = sentTo)

private suspend fun AgeGroupRepository.ageGroupOrNotFound(id: String) =
    getAgeGroupById(id) ?: throw NotFoundException("Age group not found")

/** What an emailed invite says: who invites, to which age group of which space, and the code. */
data class StaffInviteEmail(
    val ownerName: String,
    val ageGroupName: String,
    val spaceName: String,
    val code: String,
    val expiresAt: Instant,
    // "pt" for Portuguese; anything else is English.
    val language: String?
)

/** Sends an invite code by email (CoachEmailService); whether it went. */
interface StaffInviteMailer {
    suspend fun sendStaffInvite(to: String, email: StaffInviteEmail): Boolean
}

/**
 * The owner creates a code for one age group, valid for [validity]. With an email address, the code is
 * also emailed there, in the owner's app language, and shown with the address. At most
 * [dailyEmails] a day per owner, so the app can't be used to send mail in bulk. If the email can't be
 * sent, the code is withdrawn again and the owner is told (a 400).
 */
class CreateStaffInviteUseCase(
    private val invites: StaffInviteRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val mailer: StaffInviteMailer,
    private val clock: Clock = Clock.System,
    private val validity: Duration = 7.days,
    private val dailyEmails: Int = DAILY_EMAILED_INVITES
) {
    suspend operator fun invoke(userId: String, ageGroupId: String, request: CreateStaffInviteRequest = CreateStaffInviteRequest()): StaffInvite {
        val email = request.email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (email != null && !EMAIL.matches(email)) throw ValidationException("That email address isn't valid")
        val (invite, letter) = transactionManager.runInTransaction {
            val ageGroup = ageGroupRepository.ageGroupOrNotFound(ageGroupId)
            accessPolicy.requireManageStaff(userId, GameScope(ageGroup.clubId, ageGroupId = ageGroupId))
            val now = clock.now()
            if (email != null && invites.countEmailedSince(userId, now - 1.days) >= dailyEmails) {
                throw ValidationException("Too many invites emailed today; share the code instead")
            }
            // 31^6 codes: a clash is rare, but never reuse one.
            val code = generateSequence { InviteCodes.generate() }.take(10).firstOrNull { invites.findByCode(it) == null }
                ?: throw ConflictException("Couldn't create an invite code, try again")
            val stored = StoredStaffInvite(newInviteId(), code, ageGroupId, userId, now, now + validity, acceptedBy = null, sentTo = email)
            invites.create(stored)
            val letter = email?.let {
                val owner = userRepository.findById(userId)
                StaffInviteEmail(
                    ownerName = owner?.let { o -> "${o.firstName} ${o.lastName}".trim() }.orEmpty(),
                    ageGroupName = ageGroup.name,
                    spaceName = clubRepository.getClubById(ageGroup.clubId)?.name.orEmpty(),
                    code = InviteCodes.display(code),
                    expiresAt = stored.expiresAt,
                    language = request.language
                )
            }
            stored.toModel(ageGroup) to letter
        }
        // Sent once the code is stored; a code whose email didn't go is withdrawn, not left unknown.
        if (email != null && letter != null && !mailer.sendStaffInvite(email, letter)) {
            transactionManager.runInTransaction { invites.revoke(ageGroupId, InviteCodes.normalize(invite.code)) }
            throw ValidationException("Couldn't send the invite email")
        }
        return invite
    }

    companion object {
        const val DAILY_EMAILED_INVITES = 20
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
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
 * code's age group. Being in other spaces, their own included, doesn't matter. The space's owner is
 * told who joined (a push, best effort, after the transaction).
 */
class AcceptStaffInviteUseCase(
    private val invites: StaffInviteRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val clubRepository: ClubRepository,
    private val transactionManager: TransactionManager,
    private val userRepository: UserRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val pushNotificationService: PushNotificationService,
    private val clock: Clock = Clock.System
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private class JoinedPush(val tokens: List<String>, val link: PushLink, val name: String, val ageGroupName: String)

    suspend operator fun invoke(userId: String, typedCode: String): CoachSpace {
        var push: JoinedPush? = null
        val space = join(userId, typedCode) { push = it }
        push?.let {
            try {
                pushNotificationService.sendStaffJoined(it.tokens, it.link, it.name, it.ageGroupName)
            } catch (e: Exception) {
                logger.error("Staff joined push failed", e)
            }
        }
        return space
    }

    private suspend fun join(userId: String, typedCode: String, onJoined: (JoinedPush?) -> Unit): CoachSpace = transactionManager.runInTransaction {
        val invite = invites.findByCode(InviteCodes.normalize(typedCode)) ?: throw NotFoundException("Invite code not found")
        if (invite.acceptedBy != null) throw ConflictException("This invite code has already been used")
        val now = clock.now()
        if (invite.expiresAt <= now) throw ValidationException("This invite code has expired, ask for a new one")
        val ageGroup = ageGroupRepository.ageGroupOrNotFound(invite.ageGroupId)
        val spaceId = ageGroup.clubId

        val membership = clubRepository.getMemberships(userId).firstOrNull { it.first.id == spaceId }
        val role = membership?.second
        if (role == ClubRole.CLUB_ADMIN) throw ConflictException("You own this space, so you're already on every age group")
        if (ageGroupRepository.getUserRoleInAgeGroup(userId, ageGroup.id) != null) throw ConflictException("You're already staff on ${ageGroup.name}")

        if (role == null) clubRepository.addMember(spaceId, userId, ClubRole.MEMBER)
        ageGroupRepository.addStaffToAgeGroup(listOf(AgeGroupStaff(Uuid.random().toString(), ageGroup.id, userId, AgeGroupRole.COACH)))
        // Single-use: a second, simultaneous use loses here and rolls back.
        if (!invites.markAccepted(invite.id, userId, now)) throw ConflictException("This invite code has already been used")

        val space = membership?.first ?: clubRepository.getClubById(spaceId) ?: throw NotFoundException("Space not found")

        val owners = clubRepository.getClubMembers(spaceId).filter { it.role == ClubRole.CLUB_ADMIN }.map { it.userId }
        val tokens = fcmTokenRepository.getTokensForUsers(owners).values.flatten()
        val joiner = userRepository.findById(userId)
        if (tokens.isNotEmpty() && joiner != null) {
            onJoined(JoinedPush(tokens, PushLink.ageGroup(spaceId, ageGroup.id), "${joiner.firstName} ${joiner.lastName}", ageGroup.name))
        }
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
