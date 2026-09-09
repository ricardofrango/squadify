package com.ricardofrango.clubmanager.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class ClubRole(val level: Int) {
    CLUB_ADMIN(5),
    COORDINATOR(4),
    MEMBER(1)
}

/**
 * A working role held on an **age group** (via `age_group_staff`), not a team -- teams are now just a
 * scheduling handle for matches. Only people who work at the club ever hold one of these; players and
 * family members are modelled entirely through `AgeGroupPlayer` / `UserPlayer` and never appear here.
 */
@Serializable
enum class TeamRole(val level: Int) {
    COACH(3),
    STAFF(2)
}

@Serializable
enum class UserStatus {
    ACTIVE,
    INACTIVE,
    PENDING,
    SUSPENDED
}

@Serializable
enum class ClubStatus {
    ACTIVE,
    INACTIVE,
    PENDING
}

@Serializable
enum class UserPlayerRelationship {
    FATHER,
    MOTHER,
    GUARDIAN,
    OTHER
}

@Serializable
enum class SeasonType {
    CALENDAR_YEAR, // Jan 1st - Dec 31st
    EUROPEAN_YEAR  // Jul 1st - Jun 30th
}

@Serializable
enum class MatchStatus {
    SCHEDULED,
    LIVE,
    HALF_TIME,
    FINISHED,
    CANCELLED
}

@Serializable
enum class EventType {
    GOAL,
    OWN_GOAL,
    ASSIST,
    SUBSTITUTION_IN,
    SUBSTITUTION_OUT,
    YELLOW_CARD,
    RED_CARD
}

@Serializable
data class MatchEvent(
    val id: String,
    val matchId: String,
    val type: EventType,
    val minute: Int,
    val playerId: String? = null,
    val opponentPlayerName: String? = null,
    val isOpponent: Boolean = false,
    val createdAt: Instant
)

@Serializable
data class MatchScore(
    val matchId: String,
    val ourScore: Int,
    val opponentScore: Int
)

@Serializable
data class LiveMatchUpdate(
    val event: MatchEvent? = null,
    val score: MatchScore,
    val status: MatchStatus? = null
)

@Serializable
data class CreateMatchEventRequest(
    val type: EventType,
    val minute: Int,
    val playerId: String? = null,
    val opponentPlayerName: String? = null,
    val isOpponent: Boolean = false
)

@Serializable
data class User(
    val id: String,
    val email: String,
    val phone: String? = null,
    val firstName: String,
    val lastName: String,
    val avatarUrl: String? = null,
    val status: UserStatus,
    val isPlatformAdmin: Boolean = false,
    val createdAt: Instant
)

@Serializable
data class Club(
    val id: String,
    val name: String,
    val shortName: String? = null,
    val logoUrl: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val status: ClubStatus,
    val createdAt: Instant
)

/**
 * Response for `GET /clubs/{clubId}`. Carries [canEditClub] alongside the [club] so the client can
 * tell an empty seasons list apart for an admin/coordinator (who should be told a season is required)
 * versus a plain member (who should just see "no seasons yet").
 */
@Serializable
data class ClubDetailsResponse(
    val club: Club,
    val canEditClub: Boolean,
)

@Serializable
data class Season(
    val id: String,
    val clubId: String,
    val name: String,
    val type: SeasonType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val active: Boolean
)

@Serializable
data class AgeGroup(
    val id: String,
    val clubId: String,
    val name: String,
    val seasonId: String,
    val birthYear: Int
)

@Serializable
data class Team(
    val id: String,
    val clubId: String,
    val ageGroupId: String,
    val seasonId: String,
    val name: String,
    val code: String? = null,
    val active: Boolean
)

@Serializable
data class Player(
    val id: String,
    val clubId: String,
    val firstName: String,
    val lastName: String,
    val birthDate: LocalDate,
    val shirtNumber: Int? = null,
    val photoUrl: String? = null,
    val active: Boolean
)

/**
 * A player's membership of an age-group pool. Many-to-many: a player can sit in several age groups at
 * once (their own birth-year group plus any older one they're eligible to play up into). The
 * `(ageGroupId, playerId)` pair is unique. Teams within an age group all draw from this same pool;
 * who actually plays a given match is decided per-match via `MatchCallUp`.
 */
@Serializable
data class AgeGroupPlayer(
    val id: String,
    val ageGroupId: String,
    val playerId: String
)

@Serializable
data class AgeGroupStaff(
    val id: String,
    val ageGroupId: String,
    val userId: String,
    val role: TeamRole
)

@Serializable
data class ClubMember(
    val id: String,
    val clubId: String,
    val userId: String,
    val role: ClubRole,
    val active: Boolean
)

@Serializable
enum class InviteType {
    CLUB_MEMBER,
    PARENT_PLAYER
}

@Serializable
data class Invite(
    val id: String,
    val email: String,
    val clubId: String,
    val type: InviteType,
    val role: ClubRole? = null,
    val playerId: String? = null,
    val relationship: UserPlayerRelationship? = null,
    val createdAt: Instant
)

@Serializable
data class UserPlayer(
    val id: String,
    val userId: String,
    val playerId: String,
    val relationship: UserPlayerRelationship
)

// Creation Requests

@Serializable
data class CreateSeasonRequest(
    val clubId: String,
    val name: String? = null,
    val type: SeasonType,
    val startDate: LocalDate,
    val endDate: LocalDate
)

@Serializable
data class CreateAgeGroupsRequest(
    val clubId: String,
    val seasonId: String,
    val items: List<AgeGroupItemRequest>
)

@Serializable
data class AgeGroupItemRequest(
    val name: String,
    val birthYear: Int
)

@Serializable
data class CreateTeamRequest(
    val clubId: String,
    val ageGroupId: String,
    val seasonId: String,
    val name: String,
    val code: String? = null
)

/**
 * Batch team creation: all [items] are created under the same [seasonId]/[ageGroupId] in one
 * transaction (all or nothing), mirroring [CreateAgeGroupsRequest]. The age group must belong to
 * both the club and the season, same rule [CreateTeamRequest] enforces for the single case.
 */
@Serializable
data class CreateTeamsRequest(
    val clubId: String,
    val seasonId: String,
    val ageGroupId: String,
    val items: List<TeamItemRequest>
)

@Serializable
data class TeamItemRequest(
    val name: String,
    val code: String? = null
)

@Serializable
data class CreatePlayersRequest(
    val clubId: String,
    val players: List<CreatePlayer>
)

@Serializable
data class PlayerParentRequest(
    val email: String,
    val relationship: UserPlayerRelationship
)

@Serializable
data class CreatePlayer(
    val firstName: String,
    val lastName: String,
    val birthDate: LocalDate,
    val shirtNumber: Int? = null,
    val photoUrl: String? = null,
    val email: String? = null,
    val parents: List<PlayerParentRequest> = emptyList()
)

@Serializable
data class AddClubMembersRequest(
    val emails: List<String>
)

@Serializable
data class UpdateClubMemberRoleRequest(
    val role: ClubRole
)

@Serializable
data class UpdatePlayerRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val birthDate: LocalDate? = null,
    val shirtNumber: Int? = null,
    val photoUrl: String? = null,
    val email: String? = null,
    val parents: List<PlayerParentRequest>? = null
)

@Serializable
data class UpdateClubRequest(
    val name: String? = null,
    val shortName: String? = null,
    val logoUrl: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val status: ClubStatus? = null
)

@Serializable
data class UpdateSeasonRequest(
    val name: String? = null,
    val type: SeasonType? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val active: Boolean? = null
)

@Serializable
data class UpdateAgeGroupRequest(
    val name: String? = null,
    val birthYear: Int? = null
)

@Serializable
data class UpdateTeamRequest(
    val name: String? = null,
    val code: String? = null,
    val active: Boolean? = null
)

@Serializable
data class UpdateOpponentRequest(
    val name: String? = null,
    val shortName: String? = null,
    val logoUrl: String? = null
)

/** Body of `POST /age-groups/{ageGroupId}/players`. Every player must already belong to the club and
 * be age-eligible for the group (`player.birthDate.year >= ageGroup.birthYear`). Players already in
 * the group are ignored, so the call is idempotent. */
@Serializable
data class AddPlayersToAgeGroupRequest(
    val playerIds: List<String>
)

@Serializable
data class AddStaffToAgeGroup(
    val userId: String,
    val role: TeamRole
)

@Serializable
data class AddStaffToAgeGroupRequest(
    val staff: List<AddStaffToAgeGroup>
)

@Serializable
data class UpdateActiveStatusRequest(
    val active: Boolean
)

@Serializable
data class AgeGroupStaffMember(
    val userId: String,
    val firstName: String,
    val lastName: String,
    val role: TeamRole,
    val avatarUrl: String? = null
)

@Serializable
enum class MatchOutcome { WIN, DRAW, LOSS }

/**
 * One finished match for a team, with the final [score][ourScore]/[opponentScore] and the resulting
 * [outcome] from this team's point of view. Fed to the "Recent results" list on the team screen.
 */
@Serializable
data class TeamMatchResult(
    val match: Match,
    val ourScore: Int,
    val opponentScore: Int,
    val outcome: MatchOutcome
)

/**
 * Everything attached to one team, for its detail screen. A team is only a scheduling handle under an
 * [ageGroup] / [season]: its own content is the fixture list ([upcomingMatches] / [recentResults]),
 * while players and staff belong to the age group. [poolSize] and [staff] are surfaced read-only so
 * the screen can point back to the age group; [siblingTeamNames] are the other teams sharing that
 * same player pool. [opponents] is the club's opponent list so the client can resolve
 * `match.opponentId`. [canManage] gates the edit / schedule actions -- true for a club
 * admin/coordinator or a coach of this age group, same idea as `AgeGroupDetails.canManage`.
 */
@Serializable
data class TeamDetails(
    val team: Team,
    val club: Club,
    val ageGroup: AgeGroup,
    val season: Season,
    val poolSize: Int,
    val siblingTeamNames: List<String>,
    val staff: List<AgeGroupStaffMember>,
    val upcomingMatches: List<Match>,
    val recentResults: List<TeamMatchResult>,
    val opponents: List<Opponent>,
    val canManage: Boolean
)

/**
 * Everything attached to one age group, for its detail screen: the group and its [season], the full
 * player [pool][players], the [staff], the [teams] under it (all sharing the same pool), and the
 * upcoming matches across those teams ([opponents] is the club's opponent list so the client can
 * resolve `match.opponentId` to a name/logo). [canManage] is true for a club admin/coordinator or a
 * coach assigned to this age group -- it gates the add/edit actions, same idea as
 * `ClubDetailsResponse.canEditClub`.
 */
@Serializable
data class AgeGroupDetails(
    val ageGroup: AgeGroup,
    val club: Club,
    val season: Season,
    val players: List<Player>,
    val staff: List<AgeGroupStaffMember>,
    val teams: List<Team>,
    val upcomingMatches: List<Match>,
    val opponents: List<Opponent>,
    val canManage: Boolean
)

@Serializable
enum class CallUpStatus {
    CALLED_UP,
    CONFIRMED,
    DECLINED,
    NOT_AVAILABLE,
    STANDBY
}

@Serializable
data class Opponent(
    val id: String,
    val clubId: String,
    val name: String,
    val shortName: String? = null,
    val logoUrl: String? = null
)

@Serializable
data class Match(
    val id: String,
    val clubId: String,
    val teamId: String,
    val opponentId: String,
    val location: String? = null,
    val dateTime: Instant,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val notes: String? = null
)

@Serializable
data class MatchCallUp(
    val id: String,
    val matchId: String,
    val playerId: String,
    val status: CallUpStatus,
    val notes: String? = null
)

@Serializable
data class MatchCallUpWithPlayer(
    val callUp: MatchCallUp,
    val player: Player
)

/**
 * The match plus everything its detail screen needs: the [opponent], the current [callUps], the
 * [team] / [ageGroup] / [season] it belongs to, and the age group's [poolPlayers] (who can be added
 * to the call-up). [canManage] is true for a club admin/coordinator or a coach of the age group --
 * it gates the edit / call-up / lifecycle actions.
 */
@Serializable
data class MatchDetails(
    val match: Match,
    val opponent: Opponent,
    val team: Team,
    val ageGroup: AgeGroup,
    val season: Season,
    val callUps: List<MatchCallUpWithPlayer>,
    val poolPlayers: List<Player>,
    val canManage: Boolean
)

/** Body of `PATCH /matches/{matchId}`. Every field is optional; nulls are left untouched. */
@Serializable
data class UpdateMatchRequest(
    val opponentId: String? = null,
    val dateTime: Instant? = null,
    val location: String? = null,
    val notes: String? = null
)

@Serializable
data class CreateOpponentRequest(
    val name: String,
    val shortName: String? = null,
    val logoUrl: String? = null
)

@Serializable
data class CreateMatchRequest(
    val teamId: String,
    val opponentId: String,
    val location: String? = null,
    val dateTime: Instant,
    val notes: String? = null
)

@Serializable
data class UpdateCallUpRequest(
    val status: CallUpStatus,
    val notes: String? = null
)

@Serializable
data class UpdateMatchStatusRequest(
    val status: MatchStatus
)

@Serializable
data class ManageCallUpRequest(
    val playerIds: List<String>
)

// Dashboards

@Serializable
data class SuperAdminDashboard(
    val totalClubs: Long,
    val totalUsers: Long,
    val activeSubscriptions: Int, // Placeholder for future use
    val recentClubs: List<Club>
)

@Serializable
data class ClubAdminDashboard(
    val totalTeams: Long,
    val totalPlayers: Long,
    val totalCoaches: Long,
    val upcomingMatches: List<Match>,
    val activeSeasons: List<Season>
)

@Serializable
data class ParentDashboardChild(
    val playerId: String,
    val firstName: String,
    val lastName: String,
    val upcomingMatches: List<Match>,
    val recentEvents: List<MatchEvent>
)

@Serializable
data class ParentDashboard(
    val children: List<ParentDashboardChild>
)

// Club Home (the default tab of the club screen)

/** One row of the Home "Next up" list: the soonest upcoming match for a team that has one. Teams
 * with no scheduled match don't produce a row. [callUpsConfirmed] / [callUpsTotal] back the `X/Y`
 * pill (Y = call-ups on the match, X = how many are CONFIRMED). */
@Serializable
data class ClubHomeNextUp(
    val matchId: String,
    val teamId: String,
    val teamName: String,
    val opponentName: String,
    val location: String? = null,
    val dateTime: Instant,
    val callUpsConfirmed: Int,
    val callUpsTotal: Int
)

@Serializable
enum class ClubHomeAlertType {
    /** A coordinator alert: an age group in the current season has no coach in `age_group_staff`. */
    AGE_GROUP_NEEDS_COACH,
    /** A coach alert: matches within 5 days for age groups they coach that have no call-ups yet. */
    CALLUPS_UNSENT
}

/** One "Needs you" row. The client formats the copy from these parts so it stays localized. */
@Serializable
data class ClubHomeAlert(
    val type: ClubHomeAlertType,
    val count: Int,
    // AGE_GROUP_NEEDS_COACH only:
    val ageGroupId: String? = null,
    val ageGroupName: String? = null,
    // CALLUPS_UNSENT only -- the day those matches fall on:
    val date: LocalDate? = null
)

/**
 * Everything behind the club Home tab. Counts for the stat grid, one [nextUp] row per team with an
 * upcoming match, and role-scoped [alerts] ("Needs you"): coordinators get AGE_GROUP_NEEDS_COACH,
 * coaches get CALLUPS_UNSENT, someone who is both gets both.
 */
@Serializable
data class ClubHome(
    val club: Club,
    val seasonName: String? = null,
    val role: ClubRole,
    val teamCount: Int,
    val playerCount: Int,
    val coachCount: Int,
    val matchesThisWeek: Int,
    val nextUp: List<ClubHomeNextUp>,
    val alerts: List<ClubHomeAlert>
)
