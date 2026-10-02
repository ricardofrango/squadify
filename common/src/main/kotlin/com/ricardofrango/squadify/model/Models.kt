package com.ricardofrango.squadify.model

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

/**
 * Actions a club admin/coordinator can tune the minimum age-group-staff role for (see
 * `TECHNICAL_DECISIONS.md` on configurable permissions). Each maps to one or more server use cases that
 * already gate on a [TeamRole] threshold today -- this just makes that threshold a per-club setting
 * instead of a hardcoded constant. Deliberately NOT configurable, by design: who may change these
 * settings (always `ClubRole.COORDINATOR`+, never a [TeamRole]), and every *other* structural club-setup
 * action (seasons/age groups/members/staff assignment), which stays coordinator-only -- creating a team
 * used to be in that fixed group too, until a coach who'd just finished setting up an age group turned
 * out to have no way to add a second team to it themselves (§37).
 */
@Serializable
enum class ConfigurablePermission {
    CREATE_PLAYERS,
    EDIT_PLAYER,
    ASSIGN_PLAYERS_TO_AGE_GROUP,
    CREATE_OPPONENT,
    EDIT_OPPONENT,
    CREATE_TEAM,
    // Pre-match: scheduling the fixture itself (MANAGE_MATCH) and building the pre-match squad list
    // (MANAGE_CALL_UPS) are kept as two separate settings from everything that happens once the match
    // is actually live.
    MANAGE_MATCH,
    MANAGE_CALL_UPS,
    // Everything from kickoff onward -- starting XI, pause/resume, clock correction, status changes,
    // AND logging goals/cards/substitutions (a substitution is itself just a match event, see
    // EventType.SUBSTITUTION_IN/OUT) -- is one setting: once the match is live, it's all the same
    // moment and the same person is typically operating all of it. There is deliberately no separate
    // "log match events" action anymore.
    MANAGE_LIVE_MATCH
}

/** The threshold each [ConfigurablePermission] falls back to until a club explicitly overrides it. */
val ConfigurablePermission.defaultMinTeamRole: TeamRole
    get() = if (this == ConfigurablePermission.MANAGE_LIVE_MATCH) TeamRole.STAFF else TeamRole.COACH

/**
 * One club's setting for one [ConfigurablePermission]. [minTeamRole] `null` means "coordinator-only" --
 * the age-group-staff path is disabled entirely for this action, leaving only the (fixed) `COORDINATOR`+
 * club-role floor every configurable action also always carries.
 */
@Serializable
data class ClubPermissionSetting(
    val action: ConfigurablePermission,
    val minTeamRole: TeamRole?
)

/** `GET /clubs/{clubId}/permissions` -- always one entry per [ConfigurablePermission], override-or-default. */
@Serializable
data class ClubPermissionsResponse(
    val settings: List<ClubPermissionSetting>
)

/** `PATCH /clubs/{clubId}/permissions` -- replaces the club's full set of overrides. */
@Serializable
data class UpdateClubPermissionsRequest(
    val settings: List<ClubPermissionSetting>
)

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

/**
 * The sport a club runs. [teamSizes] is the a-side counts that sport is played with -- a code
 * constant (the rules of the sport, identical for every club, not per-deployment config). An age
 * group picks its default from this list; a team can then override it. Everything else (age groups,
 * teams, matches, call-ups) is sport-agnostic.
 */
@Serializable
enum class SportType(val teamSizes: List<Int>) {
    FOOTBALL(listOf(4, 5, 7, 9, 11)),
    FUTSAL(listOf(4, 5))
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

/** What kind of fixture a match is -- chosen when scheduling it. */
@Serializable
enum class MatchType { FRIENDLY, CHAMPIONSHIP, TOURNAMENT }

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
    val createdAt: Instant,
    // Which half the event happened in (1 or 2), recorded by the server when it's logged: 1 while
    // the first half is live or during the break, 2 once the second half has started. Minutes alone
    // can't tell (first-half stoppage time overlaps the second half's first minutes). Null for events
    // logged before this existed.
    val period: Int? = null,
    // For an ASSIST: the GOAL it set up (logged together, see CreateMatchEventRequest.assistPlayerId).
    // Null for anything else, and for assists logged on their own before this existed.
    val relatedEventId: String? = null
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
    // The match's own row, set whenever any of its fields change (status, kickoffAt,
    // secondHalfStartedAt, pausedAt) -- kickoff, half-time, second-half resume, full-time, cancel,
    // pause, and resume all publish this. Carries the whole row rather than individual delta fields
    // (e.g. a bare `status: MatchStatus?`, this field's previous shape) deliberately: a field that's
    // merely absent from one update (because that event didn't touch it) is indistinguishable from one
    // being deliberately cleared back to null (e.g. [Match.pausedAt] on resume) unless the whole object
    // is swapped in wholesale -- the same reasoning behind ClubMatchesUpdate's [Match] field.
    val match: Match? = null,
    // Set instead of [event] when this update is announcing that an event was removed (see
    // DeleteMatchEventUseCase) -- listeners drop it from their local event list.
    val deletedEventId: String? = null
)

/**
 * Broadcast on the club-wide `/clubs/{clubId}/matches/live` socket -- unlike [LiveMatchUpdate] (one
 * match's own event log/score, subscribed to only while that match's live console is open), this
 * covers the whole fixture list: a match created, its schedule fields changed, its status changing
 * (including kickoff), or cancelled all surface as [match] carrying the updated row; a permanent
 * removal surfaces as [deletedMatchId] instead; a goal logged or removed during a live match surfaces
 * as [score] (its own [MatchScore.matchId] says which match) rather than the whole [match], since
 * nothing else about the row changed. Exactly one of the three is set per update. Whoever's looking at
 * a club's Matches tab subscribes for as long as that tab is visible, so the list itself (including its
 * live score badges) stays live without a manual refresh -- see TECHNICAL_DECISIONS.md's push-
 * notifications section for why this exists alongside (not instead of) the push notifications for the
 * same events: a push reaches a closed app, this only patches an already-open screen while it's on
 * screen.
 */
@Serializable
data class ClubMatchesUpdate(
    val match: Match? = null,
    val deletedMatchId: String? = null,
    val score: MatchScore? = null
)

@Serializable
data class CreateMatchEventRequest(
    val type: EventType,
    val minute: Int,
    val playerId: String? = null,
    val opponentPlayerName: String? = null,
    val isOpponent: Boolean = false,
    // GOAL only, optional: who assisted it. The goal and its ASSIST event are saved together, the
    // assist linked to the goal through MatchEvent.relatedEventId.
    val assistPlayerId: String? = null
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
    // Where home matches are actually played, when that differs from [address] (the official,
    // administrative address) -- e.g. a club office address vs. a municipal stadium. Plain text, same
    // shape as `Match.location`, so a home match's location field can default to this verbatim.
    val stadiumLocation: String? = null,
    val status: ClubStatus,
    val createdAt: Instant,
    // Defaulted so the many `Club(...)` test/preview constructions stay valid; the choice is forced
    // where a human actually makes it (`ProvisionClubRequest.sport`).
    val sport: SportType
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
    // The age group's birth-year span. Most age groups (football) cover a single birth year, where
    // startYear == endYear. Some (typically futsal) span two consecutive birth years to have enough
    // players -- e.g. Benjamins covering 2016 and 2017 is startYear = 2017, endYear = 2016.
    // Eligibility is always checked against `endYear` (the oldest birth year allowed): a player can
    // play if `player.birthDate.year >= ageGroup.endYear`, same rule as the old single-year field.
    val startYear: Int,
    val endYear: Int,
    // The a-side count this age group's teams play by default (one of `club.sport.teamSizes`).
    // Nullable so age groups created before this existed still load; a team can override it.
    val teamSize: Int? = null
)

/**
 * Whether two birth-year spans (each `endYear..startYear`, oldest to youngest) share any year. Age
 * groups in the same season must not: a player's birth year has to point to one age group, not two.
 * Shared by the server's create/update checks and the client's Edit age group screen.
 */
fun birthYearsOverlap(startYear: Int, endYear: Int, otherStartYear: Int, otherEndYear: Int): Boolean =
    endYear <= otherStartYear && otherEndYear <= startYear

@Serializable
data class Team(
    val id: String,
    val clubId: String,
    val ageGroupId: String,
    val seasonId: String,
    val name: String,
    val code: String? = null,
    val active: Boolean,
    // The a-side count this team plays. Pre-filled from `ageGroup.teamSize` when the team is created,
    // but can be set to any of `club.sport.teamSizes`. Null only for teams created before this field.
    val teamSize: Int? = null
)

@Serializable
enum class PlayerPosition { GOALKEEPER, DEFENDER, MIDFIELD, WINGER, STRIKER }

@Serializable
data class Player(
    val id: String,
    val clubId: String,
    val firstName: String,
    val lastName: String,
    val birthDate: LocalDate,
    val shirtNumber: Int? = null,
    val photoUrl: String? = null,
    val active: Boolean,
    val position: PlayerPosition? = null,
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    // Free text (name + phone, or however the club records it) -- no structured contact model yet.
    val emergencyContact: String? = null
)

/**
 * Everything shown on the player profile screen: the player record plus derived stats (goals,
 * assists, times called up -- all-time, no season/career split yet) and the player's most recent
 * logged match events. [guardiansLinked] is a plain count -- who they are isn't shown on this screen.
 * [canEditPlayer] is a non-throwing probe of the same club-configurable `ConfigurablePermission.EDIT_PLAYER`
 * gate `UpdatePlayerUseCase` actually enforces (resolved against [ageGroupId], the player's primary
 * age group), not a fixed coordinator-only check -- a coach a club has allowed to edit players (the
 * default) needs to see the edit action too, not just a coordinator/admin.
 */
@Serializable
data class PlayerProfile(
    val player: Player,
    val ageGroupId: String?,
    val ageGroupLabel: String?,
    val seasonLabel: String?,
    val goals: Int,
    val assists: Int,
    val timesCalledUp: Int,
    val guardiansLinked: Int,
    val recentEvents: List<PlayerProfileEvent>,
    val canEditPlayer: Boolean
)

/** One logged event for [PlayerProfile.recentEvents], enriched with the match it happened in. */
@Serializable
data class PlayerProfileEvent(
    val type: EventType,
    val minute: Int,
    val opponentName: String,
    val matchDate: Instant
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
    // Single-year age groups send startYear == endYear; see AgeGroup for the span semantics.
    val startYear: Int,
    val endYear: Int,
    val teamSize: Int? = null
)

@Serializable
data class CreateTeamRequest(
    val clubId: String,
    val ageGroupId: String,
    val seasonId: String,
    val name: String,
    val code: String? = null,
    val teamSize: Int? = null
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
    val code: String? = null,
    val teamSize: Int? = null
)

@Serializable
data class CreatePlayersRequest(
    val clubId: String,
    val players: List<CreatePlayer>,
    // Set when this request originates from an age-group context (e.g. the "+ Add new player"
    // shortcut on that age group's "Add players" screen) -- lets a coach of specifically THIS age
    // group create players, not just a club admin/coordinator. Null for the standalone club-roster
    // entry points (Club Details' "Add players to club"), which stay coordinator-only. See
    // CreatePlayersUseCase.
    val ageGroupId: String? = null
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
    val parents: List<PlayerParentRequest> = emptyList(),
    val position: PlayerPosition? = null,
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val emergencyContact: String? = null
)

@Serializable
data class AddClubMembersRequest(
    val emails: List<String>,
    // The club role they join with: Member or Coordinator. Admin can only be granted once they're in
    // (design 34) -- the server rejects ADMIN here.
    val role: ClubRole = ClubRole.MEMBER
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
    val parents: List<PlayerParentRequest>? = null,
    val position: PlayerPosition? = null,
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val emergencyContact: String? = null,
    // Same role as CreatePlayersRequest.ageGroupId -- set when the caller knows which age group this
    // edit is happening in context of, so a coach (not just a coordinator+) can be authorized via
    // ConfigurablePermission.EDIT_PLAYER. Omitted from a generic entry point that has no age-group
    // context: only coordinator+ can edit from there, same as before this field existed.
    val ageGroupId: String? = null
)

@Serializable
data class UpdateClubRequest(
    val name: String? = null,
    val shortName: String? = null,
    val logoUrl: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val stadiumLocation: String? = null,
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
    val startYear: Int? = null,
    val endYear: Int? = null,
    // The default a-side format for teams created from now on (one of the club sport's teamSizes);
    // existing teams keep their own. Null leaves it unchanged.
    val teamSize: Int? = null
)

@Serializable
data class UpdateTeamRequest(
    val name: String? = null,
    val code: String? = null,
    val active: Boolean? = null,
    // One of the club sport's `teamSizes`; null leaves it unchanged.
    val teamSize: Int? = null
)

@Serializable
data class UpdateOpponentRequest(
    val name: String? = null,
    val shortName: String? = null,
    val logoUrl: String? = null,
    // Same role as CreateOpponentRequest.ageGroupId -- see UpdatePlayerRequest.ageGroupId's doc for why
    // this is optional rather than required.
    val ageGroupId: String? = null
)

/** Body of `POST /age-groups/{ageGroupId}/players`. Every player must already belong to the club and
 * be age-eligible for the group (`player.birthDate.year >= ageGroup.endYear`). Players already in
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

/** One row of `GET /clubs/{clubId}/members` -- who to pick from when assigning an age group's
 * coaches/staff (AddStaffToAgeGroupRequest only accepts userIds already in the club). */
@Serializable
data class ClubMemberSummary(
    val userId: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val role: ClubRole
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
 * `match.opponentId`. [canManageMatch] gates scheduling a match for this team -- a non-throwing probe
 * of the same club-configurable `ConfigurablePermission.MANAGE_MATCH` gate `CreateMatchUseCase`
 * actually enforces, not a fixed coordinator-only check.
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
    // Scores for any LIVE/HALF_TIME entry in [upcomingMatches], keyed by Match.id -- SCHEDULED entries
    // have none. [recentResults] already carries its own score via TeamMatchResult, so this only
    // exists to let a live match in the "upcoming" list show its running score too.
    val upcomingMatchScores: Map<String, MatchScore> = emptyMap(),
    val recentResults: List<TeamMatchResult>,
    val opponents: List<Opponent>,
    val canManageMatch: Boolean,
    // Coordinator+: the staff chips open Member details (design 35), which only they can read.
    val canViewMembers: Boolean = false,
    // The same gate UpdateTeamUseCase enforces (coordinator+, or this team's coach): shows "Edit team".
    val canEditTeam: Boolean = false
)

/**
 * Everything attached to one age group, for its detail screen: the group and its [season], the full
 * player [pool][players], the [staff], the [teams] under it (all sharing the same pool), and the
 * upcoming matches across those teams ([opponents] is the club's opponent list so the client can
 * resolve `match.opponentId` to a name/logo). Four independent, non-throwing permission probes, each
 * matching the real gate its action's use case enforces (see `ConfigurablePermission`/§35/§37 in
 * TECHNICAL_DECISIONS.md): [canAddStaff] gates adding staff (`AddStaffToAgeGroupUseCase`) --
 * permanently coordinator/admin-only, not club configurable, same idea as
 * `ClubDetailsResponse.canEditClub`; [canCreateTeam] gates creating a team in this age group
 * (`CreateTeamsUseCase`, `ConfigurablePermission.CREATE_TEAM`); [canManageMatch] gates scheduling a
 * match (`CreateMatchUseCase`, `ConfigurablePermission.MANAGE_MATCH`); [canAssignPlayersToAgeGroup]
 * gates adding an existing club player to this age group's pool (`AddPlayersToAgeGroupUseCase`,
 * `ConfigurablePermission.ASSIGN_PLAYERS_TO_AGE_GROUP`).
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
    // Scores for any LIVE/HALF_TIME entry in [upcomingMatches], keyed by Match.id -- SCHEDULED
    // entries have none. Lets a live match in this list show its running score.
    val upcomingMatchScores: Map<String, MatchScore> = emptyMap(),
    // Finished matches of all its teams, newest first, with their scores (Matches tab, design 33).
    val recentResults: List<TeamMatchResult> = emptyList(),
    val opponents: List<Opponent>,
    val canAddStaff: Boolean,
    val canCreateTeam: Boolean,
    val canManageMatch: Boolean,
    val canAssignPlayersToAgeGroup: Boolean,
    // Coordinator+, like Add staff (UpdateAgeGroupUseCase's gate): shows "Edit age group".
    val canEditAgeGroup: Boolean = false
)

/**
 * A single value for now -- attendance confirmation (CONFIRMED/DECLINED/NOT_AVAILABLE/STANDBY) was
 * designed for parents to self-report, but parents aren't a v1.0.0 feature yet (see AI_GUIDELINES.md
 * scope notes) and nothing else ever changed it away from CALLED_UP. Being called up now just means
 * "in the squad or not" -- kept as an enum (not dropped from MatchCallUp entirely) so re-adding real
 * attendance states later is additive, not a schema change.
 */
@Serializable
enum class CallUpStatus {
    CALLED_UP
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
    val notes: String? = null,
    // Friendly / championship / tournament. Null only for matches scheduled before this existed.
    val type: MatchType? = null,
    // Set by StartMatchUseCase at kickoff, alongside the starting XI (MatchCallUp.isStarting). Null
    // before kickoff and for matches recorded before this existed.
    val halfLengthMinutes: Int? = null,
    // Wall-clock instant the match actually kicked off (StartMatchUseCase) -- the client derives the
    // live running clock from `now - kickoffAt` rather than any local ticking counter, so it's
    // correct across app restarts/navigation and unaffected by timezone (Instant math is absolute).
    // Null before kickoff.
    val kickoffAt: Instant? = null,
    // Wall-clock instant the second half resumed (set by UpdateMatchStatusUseCase when the status
    // transitions HALF_TIME -> LIVE). The running clock is `halfLengthMinutes + (now -
    // secondHalfStartedAt)` once this is set, so it continues from the first half's length instead of
    // restarting at 0. Null until the second half starts.
    val secondHalfStartedAt: Instant? = null,
    // Wall-clock instant the running clock was paused (PauseMatchUseCase), for a stoppage mid-half --
    // distinct from HALF_TIME, which already stops the clock on its own. Null while running. On
    // resume (ResumeMatchUseCase), the server shifts whichever of [kickoffAt]/[secondHalfStartedAt] is
    // currently driving the clock forward by the paused duration and clears this back to null, so
    // [elapsedMatchSeconds] needs no separate "total paused time" bookkeeping.
    val pausedAt: Instant? = null,
    // Match-clock minute (same floor(elapsedMatchSeconds / 60) unit as MatchEvent.minute) at which the
    // first half ended -- set on the LIVE -> HALF_TIME transition, so first-half stoppage time is
    // captured. Null until then, and for matches played before this existed.
    val firstHalfEndMinute: Int? = null,
    // Match-clock minute at full time -- set on the -> FINISHED transition. Its presence also marks a
    // match whose MatchPlayerStats were computed (matches finished before this existed have neither).
    val fullTimeMinute: Int? = null
)

/**
 * The single formula for a LIVE (or paused-while-LIVE) match's running clock, in seconds since
 * kickoff -- shared by the live match console (`LiveMatchScreen`) and the club Matches list's live
 * badge (`MatchRow`) so the two can never disagree, which they did before this was extracted (the
 * list used to derive its badge from [Match.kickoffAt] alone, ignoring [Match.secondHalfStartedAt]
 * entirely). Returns 0 before kickoff. While [Match.pausedAt] is set, the clock is frozen at the
 * instant it paused rather than continuing to advance against [now].
 */
fun elapsedMatchSeconds(match: Match, now: Instant): Int {
    val secondHalfStart = match.secondHalfStartedAt
    val referenceInstant = secondHalfStart ?: match.kickoffAt ?: return 0
    val baseSeconds = if (secondHalfStart != null) (match.halfLengthMinutes ?: 0) * 60 else 0
    val clockAt = match.pausedAt ?: now
    return baseSeconds + (clockAt - referenceInstant).inWholeSeconds.toInt()
}

/**
 * One called-up player's line for a FINISHED match, computed once at full time from the call-ups
 * (who started) and the match's events, then stored -- a finished match's events can't change after
 * that (see AddMatchEventUseCase/DeleteMatchEventUseCase), so the snapshot never goes stale.
 * [rating] (0-10) is the one field set afterwards, by the coach, as part of the match report.
 */
@Serializable
data class MatchPlayerStats(
    val matchId: String,
    val playerId: String,
    val started: Boolean,
    val minutesPlayed: Int,
    val goals: Int,
    val assists: Int,
    val ownGoals: Int,
    val yellowCards: Int,
    val redCards: Int,
    val rating: Int? = null
)

/**
 * The coach's post-match report for a FINISHED match. Every text field is free text and optional.
 * The report is "pending" (listed in the Home tab's to-do) until [completedAt] is set; it stays
 * editable after that.
 */
@Serializable
data class MatchReport(
    val matchId: String,
    // Our team
    val tactic: String? = null,
    val positiveAspects: String? = null,
    val lessPositiveAspects: String? = null,
    val aspectsToImprove: String? = null,
    val teamNotes: String? = null,
    // The opponent
    val opponentTactic: String? = null,
    val opponentStrengths: String? = null,
    val opponentWeaknesses: String? = null,
    val opponentBestPlayers: String? = null,
    val opponentAdditionalInfo: String? = null,
    val completedAt: Instant? = null,
    val updatedAt: Instant,
    val updatedByUserId: String
)

/** One player's rating in a [SaveMatchReportRequest]. `null` clears it. */
@Serializable
data class MatchPlayerRatingInput(
    val playerId: String,
    val rating: Int?
)

/**
 * Saves the report text and the per-player ratings (0-10) together. [complete] marks the report as
 * done, which removes it from the Home tab's to-do; saving with `complete = false` keeps a draft.
 * Once completed a report stays completed.
 */
@Serializable
data class SaveMatchReportRequest(
    val tactic: String? = null,
    val positiveAspects: String? = null,
    val lessPositiveAspects: String? = null,
    val aspectsToImprove: String? = null,
    val teamNotes: String? = null,
    val opponentTactic: String? = null,
    val opponentStrengths: String? = null,
    val opponentWeaknesses: String? = null,
    val opponentBestPlayers: String? = null,
    val opponentAdditionalInfo: String? = null,
    val ratings: List<MatchPlayerRatingInput> = emptyList(),
    val complete: Boolean = false
)

@Serializable
data class MatchPlayerStatsWithPlayer(
    val stats: MatchPlayerStats,
    val player: Player
)

/**
 * Everything behind the match report screen: the finished match, its report (null until first
 * saved), each called-up player's stats line (which carries their rating), and whether the caller
 * may edit.
 */
@Serializable
data class MatchReportDetails(
    val match: Match,
    // For the header ("Sub-15 A vs AD Fontelas · 27 Sep"); opponent is its short name when it has one.
    val teamName: String,
    val opponentName: String,
    val report: MatchReport? = null,
    // Full name of whoever last saved the report, for "Last updated 28 Sep by Nuno Ferreira".
    val updatedByName: String? = null,
    val playerStats: List<MatchPlayerStatsWithPlayer>,
    val canEdit: Boolean
)

@Serializable
data class MatchCallUp(
    val id: String,
    val matchId: String,
    val playerId: String,
    val status: CallUpStatus,
    val notes: String? = null,
    // True for the players picked into the Starting XI at kickoff (StartMatchUseCase); false for
    // everyone else called up (the bench). Meaningless before kickoff -- the live match console's
    // pre-kickoff screen builds the XI/bench split itself from `status` alone, not this flag.
    val isStarting: Boolean = false
)

@Serializable
data class MatchCallUpWithPlayer(
    val callUp: MatchCallUp,
    val player: Player
)

/**
 * The match plus everything its detail screen needs: the [opponent], the current [callUps], the
 * [team] / [ageGroup] / [season] it belongs to, and the age group's [poolPlayers] (who can be added
 * to the call-up). Each `canManageX` flag is a direct, independent probe of the same club-configurable
 * permission its corresponding use case enforces (see `ConfigurablePermission`/§35 in
 * TECHNICAL_DECISIONS.md) -- a club can override each away from its default separately, so one flag
 * being true never implies another: [canManageMatch] gates editing/deleting the match's own record
 * (date/time/location/notes; `UpdateMatchUseCase`/`DeleteMatchUseCase`, `ConfigurablePermission.MANAGE_MATCH`);
 * [canManageCallUps] gates the pre-match squad list (`ManageCallUpsUseCase`,
 * `ConfigurablePermission.MANAGE_CALL_UPS`); [canManageLiveMatch] gates kickoff, pause/resume, clock
 * correction, status changes, and logging/deleting match events -- everything from kickoff onward
 * (`StartMatchUseCase`/`PauseMatchUseCase`/`ResumeMatchUseCase`/`SetMatchMinuteUseCase`/
 * `UpdateMatchStatusUseCase`/`AddMatchEventUseCase`/`DeleteMatchEventUseCase`,
 * `ConfigurablePermission.MANAGE_LIVE_MATCH`). The client uses these to hide (not just disable) the
 * corresponding buttons for a viewer who can't use them, rather than relying solely on the server
 * rejecting the call.
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
    val canManageMatch: Boolean,
    val canManageCallUps: Boolean,
    val canManageLiveMatch: Boolean,
    // The running/final scoreline, rebuilt from match events -- 0-0 before any goals are logged.
    val score: MatchScore,
    // The match report card's data. Null unless the match is FINISHED and the caller may read the
    // report (club coordinators/admins and the age group's staff -- never players or parents).
    val report: MatchReportSummary? = null
)

@Serializable
enum class MatchReportStatus { NOT_STARTED, DRAFT, COMPLETED }

/** What the Match Details report card needs, without loading the whole report. */
@Serializable
data class MatchReportSummary(
    val status: MatchReportStatus,
    val updatedAt: Instant? = null,
    val updatedByName: String? = null,
    val ratedPlayers: Int,
    // Called-up players with a stats line; 0 for a match finished before post-match stats existed.
    val ratablePlayers: Int,
    val canEdit: Boolean
)

/** Body of `PATCH /matches/{matchId}`. Every field is optional; nulls are left untouched. */
@Serializable
data class UpdateMatchRequest(
    val opponentId: String? = null,
    val dateTime: Instant? = null,
    val location: String? = null,
    val notes: String? = null,
    val type: MatchType? = null
)

@Serializable
data class CreateOpponentRequest(
    val name: String,
    val shortName: String? = null,
    val logoUrl: String? = null,
    // Set when this request originates from an age-group/match context (the "+ Add new opponent"
    // shortcut on the create-match screen) -- lets a coach of specifically THIS age group create
    // opponents, not just a club admin/coordinator. See CreateOpponentUseCase, and
    // CreatePlayersRequest's identical field for the same reason.
    val ageGroupId: String? = null
)

@Serializable
data class CreateMatchRequest(
    val teamId: String,
    val opponentId: String,
    val location: String? = null,
    val dateTime: Instant,
    val notes: String? = null,
    // Required: every new match is typified when it's scheduled.
    val type: MatchType
)

@Serializable
data class UpdateMatchStatusRequest(
    val status: MatchStatus
)

/**
 * Body of `PATCH /matches/{matchId}/minute` -- a manual correction to the running clock (the touchline
 * coach nudging it to match the referee's watch), server-persisted like everything else about a live
 * match's timing: shifts whichever of `kickoffAt`/`secondHalfStartedAt` is currently driving
 * [elapsedMatchSeconds] so that function evaluates to [minute] at the instant the request is handled
 * (or at [Match.pausedAt] if the match is currently paused, so a paused match's frozen display can
 * still be corrected). See SetMatchMinuteUseCase.
 */
@Serializable
data class SetMatchMinuteRequest(
    val minute: Int
)

/**
 * Body of `POST /matches/{matchId}/start` -- kicks a SCHEDULED match off: [startingPlayerIds] must be
 * exactly the team's a-side count (`Team.teamSize`, falling back to 11 when the team predates that
 * field) and each must already be called up (CALLED_UP or CONFIRMED). Every other called-up player
 * becomes the bench. See StartMatchUseCase.
 */
@Serializable
data class StartMatchRequest(
    val startingPlayerIds: List<String>,
    val halfLengthMinutes: Int
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
 * with no scheduled match don't produce a row. [callUpsTotal] backs the "N called up" pill. */
@Serializable
data class ClubHomeNextUp(
    val matchId: String,
    val teamId: String,
    val teamName: String,
    val opponentName: String,
    val location: String? = null,
    val dateTime: Instant,
    val callUpsTotal: Int
)

@Serializable
enum class ClubHomeAlertType {
    /** A coordinator alert: an age group in the current season has no coach in `age_group_staff`. */
    AGE_GROUP_NEEDS_COACH,
    /** Matches within 5 days with no call-ups yet. Admins/coordinators: the whole club; staff: their age groups, per MANAGE_CALL_UPS. */
    CALLUPS_UNSENT,
    /** A finished match whose report isn't completed. Admins/coordinators: the whole club; staff: their age groups, per MANAGE_MATCH. */
    MATCH_REPORT_PENDING
}

/** One "Needs you" row. The client formats the copy from these parts so it stays localized. */
@Serializable
data class ClubHomeAlert(
    val type: ClubHomeAlertType,
    val count: Int,
    // AGE_GROUP_NEEDS_COACH only:
    val ageGroupId: String? = null,
    val ageGroupName: String? = null,
    // CALLUPS_UNSENT: the day those matches fall on. MATCH_REPORT_PENDING: the day the match was played.
    val date: LocalDate? = null,
    // MATCH_REPORT_PENDING only -- the match to open, and the names to show:
    val matchId: String? = null,
    val teamName: String? = null,
    val opponentName: String? = null,
    // CALLUPS_UNSENT only -- the matches that day still without a call-up, so the to-do can open them.
    val matches: List<ClubHomeAlertMatch> = emptyList()
)

/** One match behind a grouped Home to-do (a day's matches with no call-up yet). */
@Serializable
data class ClubHomeAlertMatch(
    val matchId: String,
    val teamName: String,
    val opponentName: String
)

/**
 * Everything behind the club Home tab. Counts for the stat grid, one [nextUp] row per team with an
 * upcoming match, and role-scoped [alerts] ("Needs you"): admins/coordinators see everything pending in
 * the club; age-group staff see the call-up and report alerts their role can act on.
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

// ---- Club members (Club settings → Members, Member details: designs 34, 35) ----

/** One age group a member is staff on. */
@Serializable
data class MemberAgeGroupRole(
    val ageGroupId: String,
    val ageGroupName: String,
    val seasonName: String,
    val role: TeamRole,
    // The club's active season comes first in lists.
    val currentSeason: Boolean = false
)

/** One player a member is linked to (a parent/guardian link). */
@Serializable
data class MemberLinkedPlayer(
    val playerId: String,
    val name: String,
    val relationship: UserPlayerRelationship,
    val ageGroupName: String? = null,
    val shirtNumber: Int? = null
)

/** A registered club member with everything they do in the club. */
@Serializable
data class ClubMemberEntry(
    val userId: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String? = null,
    val role: ClubRole,
    val ageGroupRoles: List<MemberAgeGroupRole> = emptyList(),
    val linkedPlayers: List<MemberLinkedPlayer> = emptyList(),
    val accountCreatedAt: Instant
)

/** An email invited to join the club as [role] that hasn't registered yet. */
@Serializable
data class PendingClubInvite(
    val inviteId: String,
    val email: String,
    val role: ClubRole,
    val invitedAt: Instant
)

/**
 * Everything behind Club settings' Members tab and Member details. [viewerRole] drives what the viewer
 * may change: only admins grant or remove Admin, and the club always keeps at least one admin.
 */
@Serializable
data class ClubMembersOverview(
    val members: List<ClubMemberEntry>,
    val pendingInvites: List<PendingClubInvite>,
    val viewerUserId: String,
    val viewerRole: ClubRole
) {
    val adminCount: Int get() = members.count { it.role == ClubRole.CLUB_ADMIN }
}

