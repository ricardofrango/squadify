package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.SpacePlanRepository
import com.ricardofrango.squadify.coach.data.StoreSubscriptionRepository
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.model.AGE_GROUP_CLUB_SEPARATOR
import com.ricardofrango.squadify.model.AddAgeGroupsRequest
import com.ricardofrango.squadify.model.AddedAgeGroups
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.birthYearsOverlap
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.PlanChange
import com.ricardofrango.squadify.model.PlansResponse
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroup
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpaceDetails
import com.ricardofrango.squadify.model.SpaceNextMatch
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
 * The age-group wizard (`POST /v1/space/age-groups`): age groups for one club in the coach's own space and
 * the current season, named "{club} - {name}", each with its teams ("A", "B"…), all in one transaction. A
 * coach with no space yet gets one, named and in the sport the request gives (the first time). Refused when
 * the club's name is missing or has " - " in it, an age group is invalid (name, years, a-side size for the
 * space's sport, 1 to [MAX_TEAMS] teams), the plan has no room for them (PlanLimitException), or one shares
 * a birth year with another of the same club this season, already there or in the request (409, its
 * rows); other clubs' age groups may share them (CoachAccessPolicy.shareBirthYears).
 */
class AddAgeGroupsUseCase(
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val teamRepository: TeamRepository,
    private val coachSeasons: CoachSeasons,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: AddAgeGroupsRequest): AddedAgeGroups = transactionManager.runInTransaction {
        val clubName = request.clubName.trim().takeIf { it.isNotEmpty() } ?: throw ValidationException("The club's name is required")
        if (AGE_GROUP_CLUB_SEPARATOR in clubName) throw ValidationException("The club's name can't have \"$AGE_GROUP_CLUB_SEPARATOR\" in it")
        if (request.ageGroups.isEmpty()) throw ValidationException("At least one age group must be provided")

        val space = clubRepository.getMemberships(userId).firstOrNull { it.second == ClubRole.CLUB_ADMIN }?.first
            ?: newSpace(userId, request)
        request.ageGroups.forEach { item ->
            if (item.name.isBlank()) throw ValidationException("Age group name cannot be empty")
            if (item.startYear < item.endYear) throw ValidationException("Start year cannot be before end year")
            if (item.teamSize != null && item.teamSize !in space.sport.teamSizes) {
                throw ValidationException("Team size ${item.teamSize} is not valid for ${space.sport}")
            }
            if (item.teams !in 1..MAX_TEAMS) throw ValidationException("An age group has 1 to $MAX_TEAMS teams")
        }
        val season = coachSeasons.current(space.id)
        accessPolicy.requireRoomForAgeGroups(space.id, season.id, request.ageGroups.size)

        val names = request.ageGroups.map { "$clubName$AGE_GROUP_CLUB_SEPARATOR${it.name.trim()}" }
        val taken = ageGroupRepository.findByClubAndSeason(space.id, season.id).map { Triple(it.name, it.startYear, it.endYear) }.toMutableList()
        val conflictingRows = mutableListOf<Int>()
        request.ageGroups.forEachIndexed { index, item ->
            val clashes = taken.any { (name, start, end) ->
                accessPolicy.shareBirthYears(names[index], name) && birthYearsOverlap(item.startYear, item.endYear, start, end)
            }
            if (clashes) conflictingRows += index else taken += Triple(names[index], item.startYear, item.endYear)
        }
        if (conflictingRows.isNotEmpty()) {
            throw ConflictException("$clubName already has an age group for some of these birth years this season", rows = conflictingRows)
        }

        val ageGroups = ageGroupRepository.createAgeGroups(
            request.ageGroups.mapIndexed { index, item ->
                AgeGroup(
                    id = Uuid.random().toString(),
                    clubId = space.id,
                    name = names[index],
                    seasonId = season.id,
                    startYear = item.startYear,
                    endYear = item.endYear,
                    teamSize = item.teamSize
                )
            }
        )
        val teams = teamRepository.createTeams(
            ageGroups.zip(request.ageGroups).flatMap { (ageGroup, item) ->
                (0 until item.teams).map { index ->
                    Team(
                        id = Uuid.random().toString(),
                        clubId = space.id,
                        ageGroupId = ageGroup.id,
                        seasonId = season.id,
                        name = ('A' + index).toString(),
                        code = null,
                        active = true,
                        teamSize = item.teamSize
                    )
                }
            }
        )
        AddedAgeGroups(space, ageGroups, teams.size)
    }

    /** The coach's own space, the first time: the request's name and sport, owned by them. */
    private suspend fun newSpace(userId: String, request: AddAgeGroupsRequest): Club {
        val name = request.spaceName?.trim()?.takeIf { it.isNotEmpty() } ?: throw ValidationException("The space's name is required")
        val sport = request.sport ?: throw ValidationException("The space's sport is required")
        val space = clubRepository.createClub(
            Club(id = Uuid.random().toString(), name = name, status = ClubStatus.ACTIVE, sport = sport, createdAt = Clock.System.now())
        )
        clubRepository.addMember(space.id, userId, ClubRole.CLUB_ADMIN)
        return space
    }

    companion object {
        const val MAX_TEAMS = 5
    }
}

/**
 * Every space the coach is in, their own first, each with the age groups they have there: all of their
 * own space's, and the ones they're staff on in the others, with the space's plan and which of them it
 * locks, for their own how many age groups it has this season, its owner's name and how many opponents
 * it has. Empty before they set up or join one.
 */
class GetSpacesUseCase(
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val opponentRepository: OpponentRepository,
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
                SpaceAgeGroups(
                    space, isOwner, ageGroups, plan, locked.map { it.id }, thisSeason, left,
                    showsAds = PlanLimits.showsAds(plan),
                    ownerName = ownerName(clubRepository, space.id),
                    opponents = opponentRepository.getOpponentsByClub(space.id).size
                )
            }
    }
}

/** The space's owner's name (its `CLUB_ADMIN`), or null when it has none. */
private suspend fun ownerName(clubRepository: ClubRepository, spaceId: String): String? =
    clubRepository.getClubMembers(spaceId).firstOrNull { it.role == ClubRole.CLUB_ADMIN }
        ?.let { "${it.firstName} ${it.lastName}".trim() }

/**
 * One space the coach is in, for its details screen (`GET /v1/spaces/{id}`): the age groups they have
 * there (all of them for the owner, their own for staff), as on `/spaces`, each with its active teams'
 * names, its next game (the soonest not yet played, its opponent's name) and whether the plan locks it;
 * the current season's name; and for the owner how many more age groups the plan allows. Refused (403)
 * to anyone outside the space.
 */
class GetSpaceDetailsUseCase(
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val teamRepository: TeamRepository,
    private val matchRepository: MatchRepository,
    private val opponentRepository: OpponentRepository,
    private val transactionManager: TransactionManager,
    private val plans: CoachPlans,
    private val coachSeasons: CoachSeasons
) {
    suspend operator fun invoke(userId: String, spaceId: String): SpaceDetails = transactionManager.runInTransaction {
        val (space, role) = clubRepository.getMemberships(userId).firstOrNull { it.first.id == spaceId }
            ?: if (clubRepository.getClubById(spaceId) == null) throw NotFoundException("Space not found")
            else throw ForbiddenException("Only the space's owner and staff can see it")
        val isOwner = role == ClubRole.CLUB_ADMIN
        val ageGroups = ageGroupRepository.getAgeGroupsByClub(space.id)
            .filter { isOwner || ageGroupRepository.getUserRoleInAgeGroup(userId, it.id) != null }
        val plan = plans.planOf(space.id)
        val lockedAgeGroups = plans.lockedAgeGroups(space.id, plan)
        val season = coachSeasons.currentOrNull(space.id)
        val thisSeason = season?.let { s -> ageGroups.count { it.seasonId == s.id } } ?: 0
        val left = if (isOwner) PlanLimits.ageGroups(plan)?.let { (it - thisSeason).coerceAtLeast(0) } else null

        val teams = teamRepository.getTeamsByClub(space.id).filter { it.active }
        val ageGroupOfTeam = teams.associate { it.id to it.ageGroupId }
        val opponents = opponentRepository.getOpponentsByClub(space.id).associateBy { it.id }
        // The soonest game of each age group's teams that isn't played yet (the list is soonest first).
        val nextMatches = matchRepository.getUpcomingMatchesByClub(space.id, UPCOMING_GAMES)
            .groupBy { ageGroupOfTeam[it.teamId] }
            .mapValues { (_, matches) -> matches.first() }

        SpaceDetails(
            space = space,
            isOwner = isOwner,
            ownerName = ownerName(clubRepository, space.id),
            plan = plan,
            seasonName = season?.name,
            seasonId = season?.id,
            ageGroups = ageGroups.map { ageGroup ->
                SpaceAgeGroup(
                    ageGroup = ageGroup,
                    teams = teams.filter { it.ageGroupId == ageGroup.id }.map { it.name }.sorted(),
                    nextMatch = nextMatches[ageGroup.id]?.let { match ->
                        SpaceNextMatch(match.id, match.dateTime, opponents[match.opponentId]?.name.orEmpty())
                    },
                    locked = ageGroup.id in lockedAgeGroups || (!isOwner && userId in plans.lockedStaff(ageGroup.id, plan))
                )
            },
            ageGroupsLeft = left,
            showsAds = PlanLimits.showsAds(plan)
        )
    }

    private companion object {
        // Enough of the space's coming games to find each age group's next one.
        const val UPCOMING_GAMES = 200
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
