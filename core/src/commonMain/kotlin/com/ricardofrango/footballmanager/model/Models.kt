package com.ricardofrango.footballmanager.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    CLUB_ADMIN,
    COORDINATOR,
    COACH,
    STAFF,
    PARENT,
    PLAYER
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
enum class TeamStaffRole {
    HEAD_COACH,
    ASSISTANT_COACH,
    TEAM_MANAGER,
    PHYSIO,
    STAFF
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
    ASSIST,
    SUBSTITUTION_IN,
    SUBSTITUTION_OUT,
    YELLOW_CARD,
    RED_CARD
}

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
    val sortOrder: Int
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
    val role: TeamStaffRole
)

@Serializable
data class ClubMember(
    val id: String,
    val clubId: String,
    val userId: String,
    val role: Role,
    val active: Boolean
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
    val sortOrder: Int = 0
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
data class CreatePlayerRequest(
    val clubId: String,
    val firstName: String,
    val lastName: String,
    val birthDate: LocalDate,
    val shirtNumber: Int? = null,
    val photoUrl: String? = null
)

@Serializable
data class AddPlayerToTeamRequest(
    val teamId: String,
    val playerId: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val status: TeamPlayerStatus = TeamPlayerStatus.ACTIVE
)
