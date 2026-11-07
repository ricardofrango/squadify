package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.SpacePlanRepository
import com.ricardofrango.squadify.coach.data.StoreSubscriptionRepository
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.PlanChange
import com.ricardofrango.squadify.model.PlansResponse
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.Team
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * The first setup, right after signing up (REQUIREMENTS §5c): the coach's space, owned by them, with
 * its first age group and team in the current season. A coach owns one space at most, so a second is
 * refused; being staff in someone else's doesn't count.
 */
class SetUpSpaceUseCase(
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val teamRepository: TeamRepository,
    private val coachSeasons: CoachSeasons,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: SetUpSpaceRequest): SpaceSetup = transactionManager.runInTransaction {
        if (clubRepository.getMemberships(userId).any { it.second == ClubRole.CLUB_ADMIN }) {
            throw ConflictException("You already have a space")
        }
        val name = request.name.trim().takeIf { it.isNotEmpty() } ?: throw ValidationException("The space's name is required")
        val item = request.ageGroup
        val ageGroupName = item.name.trim().takeIf { it.isNotEmpty() } ?: throw ValidationException("Age group name cannot be empty")
        if (item.startYear < item.endYear) throw ValidationException("Start year cannot be before end year")
        val teamName = request.teamName.trim().takeIf { it.isNotEmpty() } ?: throw ValidationException("Team name cannot be empty")
        val sizes = request.sport.teamSizes
        listOfNotNull(item.teamSize, request.teamSize).firstOrNull { it !in sizes }?.let {
            throw ValidationException("Team size $it is not valid for ${request.sport}")
        }

        val space = clubRepository.createClub(
            Club(
                id = Uuid.random().toString(),
                name = name,
                status = ClubStatus.ACTIVE,
                sport = request.sport,
                createdAt = Clock.System.now()
            )
        )
        clubRepository.addMember(space.id, userId, ClubRole.CLUB_ADMIN)
        val season = coachSeasons.current(space.id)
        val ageGroup = ageGroupRepository.createAgeGroups(
            listOf(
                AgeGroup(
                    id = Uuid.random().toString(),
                    clubId = space.id,
                    name = ageGroupName,
                    seasonId = season.id,
                    startYear = item.startYear,
                    endYear = item.endYear,
                    teamSize = item.teamSize
                )
            )
        ).single()
        val team = teamRepository.createTeam(
            Team(
                id = Uuid.random().toString(),
                clubId = space.id,
                ageGroupId = ageGroup.id,
                seasonId = season.id,
                name = teamName,
                code = null,
                active = true,
                teamSize = request.teamSize ?: item.teamSize
            )
        )
        SpaceSetup(space, season, ageGroup, team)
    }
}

/**
 * Every space the coach is in, their own first, each with the age groups they have there: all of their
 * own space's, and the ones they're staff on in the others, with the space's plan and which of them it
 * locks, and for their own how many age groups it has this season. Empty before they set up or join one.
 */
class GetSpacesUseCase(
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val transactionManager: TransactionManager,
    private val plans: CoachPlans,
    private val coachSeasons: CoachSeasons
) {
    suspend operator fun invoke(userId: String): List<SpaceAgeGroups> = transactionManager.runInTransaction {
        clubRepository.getMemberships(userId)
            .sortedByDescending { it.second == ClubRole.CLUB_ADMIN }
            .map { (space, role) ->
                val isOwner = role == ClubRole.CLUB_ADMIN
                val ageGroups = ageGroupRepository.getAgeGroupsByClub(space.id)
                    .filter { isOwner || ageGroupRepository.getUserRoleInAgeGroup(userId, it.id) != null }
                val plan = plans.planOf(space.id)
                val lockedAgeGroups = plans.lockedAgeGroups(space.id, plan)
                val locked = ageGroups.filter { it.id in lockedAgeGroups || (!isOwner && userId in plans.lockedStaff(it.id, plan)) }
                val thisSeason = if (isOwner) coachSeasons.currentOrNull(space.id)?.let { season -> ageGroups.count { it.seasonId == season.id } } ?: 0 else 0
                val left = if (isOwner) PlanLimits.ageGroups(plan)?.let { (it - thisSeason).coerceAtLeast(0) } else null
                SpaceAgeGroups(space, isOwner, ageGroups, plan, locked.map { it.id }, thisSeason, left, showsAds = PlanLimits.showsAds(plan))
            }
    }
}

/**
 * Every plan as the app shows it (`GET /v1/plans`, PlanLimits), its features in [language] and its store
 * products, the plan of the space the coach owns (null: they own none) with the one they asked for, how
 * this server changes plans ([changeBy]), and a store subscription's lower plan coming at its renewal.
 */
class GetPlansUseCase(
    private val clubRepository: ClubRepository,
    private val plans: CoachPlans,
    private val subscriptions: StoreSubscriptionRepository,
    private val changeBy: PlanChange,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, language: String?): PlansResponse = transactionManager.runInTransaction {
        val own = clubRepository.getMemberships(userId).firstOrNull { (_, role) -> role == ClubRole.CLUB_ADMIN }?.first
        val ownPlan = own?.let { plans.planOf(it.id) }
        val stored = own?.let { space -> subscriptions.forSpace(space.id).filter { it.entitled } }.orEmpty()
        val pending = stored.firstOrNull { it.pendingPlan != null }
        val period = stored.firstOrNull { it.plan == ownPlan }?.let { sub ->
            PlanLimits.catalogue(null).flatMap { it.storeProducts }
                .firstOrNull { it.googleProductId == sub.productId && it.googleBasePlanId == sub.basePlanId }?.period
        }
        PlansResponse(
            PlanLimits.catalogue(language),
            ownPlan,
            own?.let { plans.requestedPlan(it.id) },
            changeBy,
            pending?.pendingPlan,
            pending?.expiresAt,
            period,
            own?.let { userId }
        )
    }
}

/** What the platform admins are emailed when an owner asks for a plan. */
data class PlanRequestEmail(
    val spaceId: String,
    val spaceName: String,
    val ownerName: String,
    val ownerEmail: String,
    val currentPlan: SpacePlan,
    val requestedPlan: SpacePlan
)

/** Tells a platform admin about a plan request by email (CoachEmailService); whether it went. */
interface PlanRequestMailer {
    suspend fun sendPlanRequest(to: String, email: PlanRequestEmail): Boolean
}

/**
 * The owner asks for another plan for their space (`POST /v1/plans/request`, REQUIREMENTS §5c), until
 * payments. Only an owner asks, for a plan that exists and isn't theirs already. With [atOnce] (on a
 * local server, never deployed: CoachConfig.deployed) the plan is set there and then, to try the plans
 * without an admin. Otherwise it's recorded, in place of an earlier request, and [adminEmails] are emailed
 * to set it with the admin's `PUT /v1/admin/spaces/{id}/plan`; the request stands if an email doesn't go,
 * since the admin's list shows it too. Returns the plans as `GET /v1/plans` does, in [language], after it.
 */
class RequestPlanUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val plans: SpacePlanRepository,
    private val mailer: PlanRequestMailer,
    private val adminEmails: Set<String>,
    private val atOnce: Boolean,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(userId: String, plan: SpacePlan, language: String? = null): PlansResponse {
        val (response, email) = transactionManager.runInTransaction {
            val space = clubRepository.getMemberships(userId).firstOrNull { (_, role) -> role == ClubRole.CLUB_ADMIN }?.first
                ?: throw ForbiddenException("Only a space's owner can ask for a plan")
            if (!PlanLimits.exists(plan)) throw ValidationException("No such plan: ${plan.id}")
            val current = plans.planOf(space.id)
            if (plan == current) throw ValidationException("The space is already on this plan")
            val email = if (atOnce) {
                plans.setPlan(space.id, plan, clock.now())
                null
            } else {
                plans.requestPlan(space.id, plan, userId, clock.now())
                val owner = userRepository.findById(userId)
                PlanRequestEmail(
                    spaceId = space.id,
                    spaceName = space.name,
                    ownerName = owner?.let { "${it.firstName} ${it.lastName}".trim() }.orEmpty(),
                    ownerEmail = owner?.email.orEmpty(),
                    currentPlan = current,
                    requestedPlan = plan
                )
            }
            PlansResponse(PlanLimits.catalogue(language), plans.planOf(space.id), plans.requestedPlan(space.id), changeBy = if (atOnce) PlanChange.INSTANT else PlanChange.REQUEST) to email
        }
        email?.let { adminEmails.forEach { to -> mailer.sendPlanRequest(to, it) } }
        return response
    }
}
