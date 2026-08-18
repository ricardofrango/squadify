package com.ricardofrango.footballmanager.data.database

import com.ricardofrango.footballmanager.model.*
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.datetime.date

object Users : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val phone = varchar("phone", 50).nullable()
    val firstName = varchar("first_name", 100)
    val lastName = varchar("last_name", 100)
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val status = enumerationByName("status", 50, UserStatus::class).default(UserStatus.ACTIVE)
    val isPlatformAdmin = bool("is_platform_admin").default(false)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val token = varchar("token", 500).uniqueIndex()
    val expiresAt = timestamp("expires_at")

    override val primaryKey = PrimaryKey(id)
}

object Clubs : Table("clubs") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val shortName = varchar("short_name", 50).nullable()
    val logoUrl = varchar("logo_url", 500).nullable()
    val email = varchar("email", 255).nullable()
    val phone = varchar("phone", 50).nullable()
    val address = varchar("address", 500).nullable()
    val status = enumerationByName("status", 50, ClubStatus::class).default(ClubStatus.ACTIVE)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object ClubMembers : Table("club_members") {
    val id = uuid("id")
    val clubId = uuid("club_id").references(Clubs.id)
    val userId = uuid("user_id").references(Users.id)
    val role = enumerationByName("role", 50, Role::class)
    val active = bool("active").default(true)

    override val primaryKey = PrimaryKey(id)
}

object Seasons : Table("seasons") {
    val id = uuid("id")
    val clubId = uuid("club_id").references(Clubs.id)
    val name = varchar("name", 100)
    val startDate = date("start_date")
    val endDate = date("end_date")
    val active = bool("active").default(true)

    override val primaryKey = PrimaryKey(id)
}

object AgeGroups : Table("age_groups") {
    val id = uuid("id")
    val clubId = uuid("club_id").references(Clubs.id)
    val seasonId = uuid("season_id").references(Seasons.id)
    val name = varchar("name", 100)
    val sortOrder = integer("sort_order").default(0)

    override val primaryKey = PrimaryKey(id)
}

object Teams : Table("teams") {
    val id = uuid("id")
    val clubId = uuid("club_id").references(Clubs.id)
    val ageGroupId = uuid("age_group_id").references(AgeGroups.id)
    val seasonId = uuid("season_id").references(Seasons.id)
    val name = varchar("name", 100)
    val code = varchar("code", 50).nullable()
    val active = bool("active").default(true)

    override val primaryKey = PrimaryKey(id)
}

object Players : Table("players") {
    val id = uuid("id")
    val clubId = uuid("club_id").references(Clubs.id)
    val firstName = varchar("first_name", 100)
    val lastName = varchar("last_name", 100)
    val birthDate = date("birth_date")
    val shirtNumber = integer("shirt_number").nullable()
    val photoUrl = varchar("photo_url", 500).nullable()
    val active = bool("active").default(true)

    override val primaryKey = PrimaryKey(id)
}

object TeamPlayers : Table("team_players") {
    val id = uuid("id")
    val teamId = uuid("team_id").references(Teams.id)
    val playerId = uuid("player_id").references(Players.id)
    val startDate = date("start_date")
    val endDate = date("end_date").nullable()
    val status = enumerationByName("status", 50, TeamPlayerStatus::class).default(TeamPlayerStatus.ACTIVE)

    override val primaryKey = PrimaryKey(id)
}

object TeamStaff : Table("team_staff") {
    val id = uuid("id")
    val teamId = uuid("team_id").references(Teams.id)
    val userId = uuid("user_id").references(Users.id)
    val role = enumerationByName("role", 50, TeamStaffRole::class)

    override val primaryKey = PrimaryKey(id)
}

object UserPlayers : Table("user_players") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val playerId = uuid("player_id").references(Players.id)
    val relationship = enumerationByName("relationship", 50, UserPlayerRelationship::class)

    override val primaryKey = PrimaryKey(id)
}
