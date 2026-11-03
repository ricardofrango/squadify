package com.ricardofrango.squadify.coach.access

import com.ricardofrango.squadify.coach.domain.CoachPlans
import com.ricardofrango.squadify.coach.domain.PlanLimits
import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.PlanLimitException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.UserStatus

/**
 * The coach server's [AccessPolicy] (TECHNICAL_DECISIONS §72). There are no roles beyond owner and
 * staff, and no configurable permissions:
 * - **the owner** (the space's `CLUB_ADMIN`, who created it and pays) may do everything, in every age
 *   group of the space, by this rule rather than by staff rows, so a new age group needs nothing more;
 * - **staff** (any other member of the space) may do everything in the age groups they're staff on
 *   (`age_group_staff`), and see and add what belongs to the whole space (opponents, players);
 * - only the owner creates age groups, manages staff and changes the space's own details (its logo);
 * - the space's plan ([CoachPlans], REQUIREMENTS §5c) locks what's over its limits, for the owner too:
 *   an age group after the first ones created in its season, and staff after the first to join an age
 *   group; stats are on Basic and Pro, and following games live and training on Pro. Those refusals are [PlanLimitException]s.
 */
class CoachAccessPolicy(
    private val userRepository: UserRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val teamRepository: TeamRepository,
    private val transactionManager: TransactionManager,
    private val plans: CoachPlans
) : AccessPolicy {

    override suspend fun requireMember(userId: String, clubId: String) {
        member(userId, clubId, AccessPolicy.DEFAULT_MESSAGE)
    }

    override suspend fun requireViewGames(userId: String, scope: GameScope) = inAgeGroup(userId, scope, AccessPolicy.DEFAULT_MESSAGE)

    override suspend fun requireManageMatch(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    override suspend fun requireManageCallUps(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    override suspend fun requireManageLiveMatch(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    // Everyone in the coach app is staff, so ratings go with the age group.
    override suspend fun requireSeeRatings(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    // The space's own details (its logo): the owner's.
    override suspend fun requireEditClub(userId: String, clubId: String, errorMessage: String) = owner(userId, clubId, errorMessage)

    override suspend fun requireViewClub(userId: String, clubId: String) = requireMember(userId, clubId)

    // The owner sees every age group; staff the ones they're on. Not the ones the plan locks for them.
    override suspend fun visibleAgeGroupIds(userId: String, clubId: String): Set<String>? = transactionManager.runInTransaction {
        val owner = member(userId, clubId, AccessPolicy.DEFAULT_MESSAGE) == OWNER
        val plan = plans.planOf(clubId)
        val locked = plans.lockedAgeGroups(clubId, plan)
        if (owner && locked.isEmpty()) return@runInTransaction null
        ageGroupRepository.getAgeGroupsByClub(clubId)
            .filter { it.id !in locked }
            .filter { owner || (ageGroupRepository.getUserRoleInAgeGroup(userId, it.id) != null && userId !in plans.lockedStaff(it.id, plan)) }
            .map { it.id }
            .toSet()
    }

    override suspend fun requireStaff(userId: String, scope: GameScope) = inAgeGroup(userId, scope, AccessPolicy.DEFAULT_MESSAGE)

    override suspend fun requireViewMembers(userId: String, clubId: String) = requireMember(userId, clubId)

    override suspend fun requireCreateAgeGroups(userId: String, clubId: String, errorMessage: String) =
        owner(userId, clubId, ONLY_THE_OWNER_CREATES_AGE_GROUPS)

    override suspend fun requireEditAgeGroup(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    override suspend fun requireManageStaff(userId: String, scope: GameScope, errorMessage: String) =
        owner(userId, scope.clubId, ONLY_THE_OWNER_MANAGES_STAFF)

    override suspend fun requireManageTeams(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    override suspend fun requireEditTeam(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    override suspend fun requireCreatePlayers(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    override suspend fun requireEditPlayer(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    override suspend fun requireManagePool(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    override suspend fun requireViewOpponents(userId: String, scope: GameScope) = inAgeGroup(userId, scope, AccessPolicy.DEFAULT_MESSAGE)

    override suspend fun requireCreateOpponent(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    override suspend fun requireEditOpponent(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    // Training: everyone is staff, so it all goes with the age group, like the games.
    override suspend fun requireViewTraining(userId: String, scope: GameScope) = inAgeGroup(userId, scope, AccessPolicy.DEFAULT_MESSAGE)

    override suspend fun requireViewTrainingPlans(userId: String, scope: GameScope) = inAgeGroup(userId, scope, AccessPolicy.DEFAULT_MESSAGE)

    override suspend fun requireManageTraining(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    override suspend fun requireRateTraining(userId: String, scope: GameScope, errorMessage: String) = inAgeGroup(userId, scope, errorMessage)

    override suspend fun requireTrainingInPlan(clubId: String) = transactionManager.runInTransaction {
        if (!PlanLimits.training(plans.planOf(clubId))) throw PlanLimitException("Training is on the Pro plan")
    }

    override suspend fun requireStatsInPlan(clubId: String) = transactionManager.runInTransaction {
        if (!PlanLimits.stats(plans.planOf(clubId))) throw PlanLimitException("Stats are on the Basic and Pro plans")
    }

    override suspend fun requireLiveInPlan(clubId: String) = transactionManager.runInTransaction {
        if (!PlanLimits.live(plans.planOf(clubId))) throw PlanLimitException("Following games live is on the Pro plan")
    }

    override suspend fun requireRoomForAgeGroups(clubId: String, seasonId: String, count: Int) = transactionManager.runInTransaction {
        val limit = PlanLimits.ageGroups(plans.planOf(clubId)) ?: return@runInTransaction
        val inSeason = ageGroupRepository.getAgeGroupsByClub(clubId).count { it.seasonId == seasonId }
        if (inSeason + count > limit) throw PlanLimitException("The space's plan allows $limit age groups a season")
    }

    /** The caller's role in the space: refused unless their account is active and they belong to it. */
    private suspend fun member(userId: String, clubId: String, errorMessage: String): ClubRole = transactionManager.runInTransaction {
        val user = userRepository.findById(userId) ?: throw ForbiddenException("User not found")
        if (user.status != UserStatus.ACTIVE) throw ForbiddenException("User account is not active")
        userRepository.getUserRoleInClub(userId, clubId) ?: throw ForbiddenException(errorMessage)
    }

    private suspend fun owner(userId: String, clubId: String, errorMessage: String) {
        if (member(userId, clubId, errorMessage) != OWNER) throw ForbiddenException(errorMessage)
    }

    /**
     * A member of the space, and, when [scope] is about an age group (given, or the team's), its
     * owner or one of its staff. With no age group in [scope], the space's members.
     */
    private suspend fun inAgeGroup(userId: String, scope: GameScope, errorMessage: String) = transactionManager.runInTransaction {
        val owner = member(userId, scope.clubId, errorMessage) == OWNER
        val ageGroupId = scope.ageGroupId
            ?: scope.teamId?.let { teamRepository.getTeamById(it)?.ageGroupId ?: if (owner) null else throw ForbiddenException(errorMessage) }
            ?: return@runInTransaction
        if (!owner) {
            // Staff rows are only trusted for an age group of this space.
            val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
            if (ageGroup?.clubId != scope.clubId || ageGroupRepository.getUserRoleInAgeGroup(userId, ageGroupId) == null) {
                throw ForbiddenException(errorMessage)
            }
        }
        val plan = plans.planOf(scope.clubId)
        if (ageGroupId in plans.lockedAgeGroups(scope.clubId, plan)) throw PlanLimitException(AGE_GROUP_LOCKED)
        if (!owner && userId in plans.lockedStaff(ageGroupId, plan)) throw PlanLimitException(STAFF_LOCKED)
    }

    private companion object {
        val OWNER = ClubRole.CLUB_ADMIN
        const val ONLY_THE_OWNER_CREATES_AGE_GROUPS = "Only the owner can create age groups"
        const val ONLY_THE_OWNER_MANAGES_STAFF = "Only the owner can manage staff"
        const val AGE_GROUP_LOCKED = "This age group is locked on the space's plan"
        const val STAFF_LOCKED = "The space's plan has no place for you on this age group"
    }
}
