package com.ricardofrango.clubmanager.data.database

import com.ricardofrango.clubmanager.model.*
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
    val role = enumerationByName("role", 50, ClubRole::class)
    val active = bool("active").default(true)

    override val primaryKey = PrimaryKey(id)
}

object Seasons : Table("seasons") {
    val id = uuid("id")
    val clubId = uuid("club_id").references(Clubs.id)
    val name = varchar("name", 100)
    val type = enumerationByName("type", 20, SeasonType::class).default(SeasonType.EUROPEAN_YEAR)
    val startDate = date("start_date")
    val endDate = date("end_date")
    val active = bool("active").default(true)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_club_season", clubId, type, startDate, endDate)
    }
}

object AgeGroups : Table("age_groups") {
    val id = uuid("id")
    val clubId = uuid("club_id").references(Clubs.id)
    val seasonId = uuid("season_id").references(Seasons.id)
    val name = varchar("name", 100)
    val birthYear = integer("birth_year").default(0)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_club_season_birthyear", clubId, seasonId, birthYear)
    }
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

object AgeGroupPlayers : Table("age_group_players") {
    val id = uuid("id")
    val ageGroupId = uuid("age_group_id").references(AgeGroups.id)
    val playerId = uuid("player_id").references(Players.id)

    override val primaryKey = PrimaryKey(id)

    init {
        // A player is in an age group at most once -- makes the "same player on two teams in one age
        // group" duplication impossible by construction.
        uniqueIndex("unique_age_group_player", ageGroupId, playerId)
    }
}

object AgeGroupStaff : Table("age_group_staff") {
    val id = uuid("id")
    val ageGroupId = uuid("age_group_id").references(AgeGroups.id)
    val userId = uuid("user_id").references(Users.id)
    val role = enumerationByName("role", 50, TeamRole::class)

    override val primaryKey = PrimaryKey(id)
}

object UserPlayers : Table("user_players") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val playerId = uuid("player_id").references(Players.id)
    val relationship = enumerationByName("relationship", 50, UserPlayerRelationship::class)

    override val primaryKey = PrimaryKey(id)
}

object Invites : Table("invites") {
    val id = uuid("id")
    val email = varchar("email", 255)
    val clubId = uuid("club_id").references(Clubs.id)
    val type = enumerationByName("type", 50, InviteType::class)
    val role = enumerationByName("role", 50, ClubRole::class).nullable()
    val playerId = uuid("player_id").references(Players.id).nullable()
    val relationship = enumerationByName("relationship", 50, UserPlayerRelationship::class).nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object Opponents : Table("opponents") {
    val id = uuid("id")
    val clubId = uuid("club_id").references(Clubs.id)
    val name = varchar("name", 255)
    val shortName = varchar("short_name", 50).nullable()
    val logoUrl = varchar("logo_url", 500).nullable()

    override val primaryKey = PrimaryKey(id)
}

object Matches : Table("matches") {
    val id = uuid("id")
    val clubId = uuid("club_id").references(Clubs.id)
    val teamId = uuid("team_id").references(Teams.id)
    val opponentId = uuid("opponent_id").references(Opponents.id)
    val location = varchar("location", 500).nullable()
    val dateTime = timestamp("date_time")
    val status = enumerationByName("status", 50, MatchStatus::class).default(MatchStatus.SCHEDULED)
    val notes = text("notes").nullable()

    override val primaryKey = PrimaryKey(id)
}

object MatchCallUps : Table("match_call_ups") {
    val id = uuid("id")
    val matchId = uuid("match_id").references(Matches.id)
    val playerId = uuid("player_id").references(Players.id)
    val status = enumerationByName("status", 50, CallUpStatus::class).default(CallUpStatus.CALLED_UP)
    val notes = text("notes").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_match_player", matchId, playerId)
    }
}

object MatchEvents : Table("match_events") {
    val id = uuid("id")
    val matchId = uuid("match_id").references(Matches.id)
    val type = enumerationByName("type", 50, EventType::class)
    val minute = integer("minute")
    val playerId = uuid("player_id").references(Players.id).nullable()
    val opponentPlayerName = varchar("opponent_player_name", 255).nullable()
    val isOpponent = bool("is_opponent").default(false)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
