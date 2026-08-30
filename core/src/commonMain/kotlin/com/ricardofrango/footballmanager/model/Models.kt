package com.ricardofrango.footballmanager.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class ClubRole(val level: Int) {
    CLUB_ADMIN(5),
    COORDINATOR(4),
    MEMBER(1)
}

@Serializable
enum class TeamRole(val level: Int) {
    COACH(3),
    STAFF(2),
    PARENT(1),
    PLAYER(1)
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
enum class TeamPlayerStatus {
    ACTIVE,
    INACTIVE,
    TRANSFERRED,
    INJURED
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

@Serializable
data class TeamPlayer(
    val id: String,
    val teamId: String,
    val playerId: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val status: TeamPlayerStatus
)

@Serializable
data class TeamStaff(
    val id: String,
    val teamId: String,
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
data class CreateAgeGroupRequest(
    val clubId: String,
    val name: String,
    val seasonId: String,
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
    val photoUrl: String? = null
)

@Serializable
data class CreatePlayerRequest(
    val clubId: String,
    val firstName: String,
    val lastName: String,
    val birthDate: LocalDate,
    val shirtNumber: Int? = null,
    val photoUrl: String? = null,
    val email: String? = null,
    val parents: List<PlayerParentRequest> = emptyList()
)

@Serializable
data class AddClubMemberRequest(
    val email: String
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
data class AddPlayersToTeamRequest(
    val teamId: String,
    val players: List<AddPlayerToTeam>
)

@Serializable
data class AddPlayerToTeam(
    val playerId: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val status: TeamPlayerStatus = TeamPlayerStatus.ACTIVE
)

@Serializable
data class AddPlayerToTeamRequest(
    val teamId: String,
    val playerId: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val status: TeamPlayerStatus = TeamPlayerStatus.ACTIVE
)

@Serializable
data class AddStaffToTeamRequest(
    val userId: String,
    val role: TeamRole
)

@Serializable
data class UpdateActiveStatusRequest(
    val active: Boolean
)

@Serializable
data class TeamStaffMember(
    val userId: String,
    val firstName: String,
    val lastName: String,
    val role: TeamRole,
    val avatarUrl: String? = null
)

@Serializable
data class TeamDetails(
    val team: Team,
    val players: List<Player>,
    val staff: List<TeamStaffMember>
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

@Serializable
data class MatchDetails(
    val match: Match,
    val opponent: Opponent,
    val callUps: List<MatchCallUpWithPlayer>
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
