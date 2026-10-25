package com.ricardofrango.squadify.data.database

import org.jetbrains.exposed.v1.core.ReferenceOption
import com.ricardofrango.squadify.model.*
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.time

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
    // Wrong current passwords in a row (Change password / Change email); see CurrentPasswordGuard.
    val passwordCheckFailures = integer("password_check_failures").default(0)

    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val token = varchar("token", 500).uniqueIndex()
    val expiresAt = timestamp("expires_at")

    override val primaryKey = PrimaryKey(id)
}

// At most one pending Forgot-password code per user (requesting a new one replaces it). Only a BCrypt
// hash of the code is stored. See RequestPasswordResetUseCase / ResetPasswordUseCase.
object PasswordResetCodes : Table("password_reset_codes") {
    val userId = uuid("user_id").references(Users.id)
    val codeHash = varchar("code_hash", 255)
    val expiresAt = timestamp("expires_at")
    val attempts = integer("attempts").default(0)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(userId)
}

// At most one pending Change-email request per user (a new one replaces it): the address it would
// switch to and a BCrypt hash of the code sent there. See RequestEmailChangeUseCase.
object EmailChangeCodes : Table("email_change_codes") {
    val userId = uuid("user_id").references(Users.id)
    val newEmail = varchar("new_email", 255)
    val codeHash = varchar("code_hash", 255)
    val expiresAt = timestamp("expires_at")
    val attempts = integer("attempts").default(0)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(userId)
}

object FcmTokens : Table("fcm_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    // Unique on the token itself, not (user_id, token) -- an FCM token identifies one specific app
    // install, not one user. If a different user logs into the same already-installed app (device
    // shared, or a logout/login without reinstalling), Firebase can hand back the very same token
    // value, now meant for the new user. Registration (a later commit) upserts on this constraint --
    // reassigns the existing row's user_id rather than inserting a duplicate -- so a token only ever
    // points at whoever is actually logged in on that install right now.
    val token = varchar("token", 4096).uniqueIndex()
    val createdAt = timestamp("created_at")

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
    val stadiumLocation = varchar("stadium_location", 500).nullable()
    val country = varchar("country", 2).default(Country.DEFAULT.code)
    val status = enumerationByName("status", 50, ClubStatus::class).default(ClubStatus.ACTIVE)
    val sport = enumerationByName("sport", 50, SportType::class)
    val createdAt = timestamp("created_at")
    // ClubTheme: both set or both null (chk_clubs_theme_pair).
    val themePrimaryColor = varchar("theme_primary_color", 7).nullable()
    val themeSecondaryColor = varchar("theme_secondary_color", 7).nullable()

    override val primaryKey = PrimaryKey(id)
}

object ClubMembers : Table("club_members") {
    val id = uuid("id")
    val clubId = uuid("club_id").references(Clubs.id)
    val userId = uuid("user_id").references(Users.id)
    val role = enumerationByName("role", 50, ClubRole::class)
    val active = bool("active").default(true)

    override val primaryKey = PrimaryKey(id)

    init {
        // One membership per user per club -- see V3__unique_club_members.sql.
        uniqueIndex("unique_club_member", clubId, userId)
    }
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
    // See AgeGroup (core Models.kt) for the span semantics -- single-year groups have
    // startYear == endYear; eligibility is always checked against endYear.
    val startYear = integer("start_year").default(0)
    val endYear = integer("end_year").default(0)
    val teamSize = integer("team_size").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_club_season_years", clubId, seasonId, startYear, endYear)
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
    val teamSize = integer("team_size").nullable()

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
    val position = enumerationByName("position", 20, PlayerPosition::class).nullable()
    val heightCm = integer("height_cm").nullable()
    val weightKg = integer("weight_kg").nullable()
    val emergencyContact = varchar("emergency_contact", 255).nullable()

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
    val role = enumerationByName("role", 50, AgeGroupRole::class)

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
    val stadiumName = varchar("stadium_name", 255).nullable()
    val stadiumAddress = varchar("stadium_address", 500).nullable()
    val contactEmail = varchar("contact_email", 255).nullable()
    val contactPhone = varchar("contact_phone", 50).nullable()
    val notes = text("notes").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Matches : Table("matches") {
    val id = uuid("id")
    val clubId = uuid("club_id").references(Clubs.id)
    // Deleting a team deletes its matches (V21).
    val teamId = uuid("team_id").references(Teams.id, onDelete = ReferenceOption.CASCADE)
    val opponentId = uuid("opponent_id").references(Opponents.id)
    val location = varchar("location", 500).nullable()
    val dateTime = timestamp("date_time")
    val status = enumerationByName("status", 50, MatchStatus::class).default(MatchStatus.SCHEDULED)
    val notes = text("notes").nullable()
    // Friendly / championship / tournament (MatchType). Null for matches scheduled before V9.
    val matchType = enumerationByName("match_type", 50, MatchType::class).nullable()
    // 2, or 1 for a single-period match (no half time) -- V10.
    val halves = integer("halves").default(2)
    // V24: HOME, AWAY or NEUTRAL; existing matches became HOME.
    val venue = enumerationByName("venue", 20, MatchVenue::class).default(MatchVenue.HOME)
    // Set at kickoff by StartMatchUseCase alongside MatchCallUps.isStarting. Null before kickoff.
    val halfLengthMinutes = integer("half_length_minutes").nullable()
    // Wall-clock instant of kickoff / second-half restart -- the client's live running clock is
    // derived from these, not any locally-ticking counter. Both null before kickoff;
    // secondHalfStartedAt stays null until the HALF_TIME -> LIVE transition.
    val kickoffAt = timestamp("kickoff_at").nullable()
    val secondHalfStartedAt = timestamp("second_half_started_at").nullable()
    // Wall-clock instant the running clock was paused (PauseMatchUseCase) -- null while running. On
    // resume, whichever of kickoffAt/secondHalfStartedAt is currently driving the clock shifts forward
    // by the paused duration and this clears back to null (see Match.pausedAt's own doc comment).
    val pausedAt = timestamp("paused_at").nullable()
    // Match-clock minutes at the end of the first half / at full time (see Match.firstHalfEndMinute /
    // fullTimeMinute). Used to compute minutes played, including stoppage time.
    val firstHalfEndMinute = integer("first_half_end_minute").nullable()
    val fullTimeMinute = integer("full_time_minute").nullable()
    // V25: the formation now ("2-3-1", it can change during the game) and the one at kickoff. Null
    // before the pitch view existed; see Formation.kt.
    val formation = varchar("formation", 20).nullable()
    val startingFormation = varchar("starting_formation", 20).nullable()

    override val primaryKey = PrimaryKey(id)
}

object MatchCallUps : Table("match_call_ups") {
    val id = uuid("id")
    val matchId = uuid("match_id").references(Matches.id, onDelete = ReferenceOption.CASCADE)
    val playerId = uuid("player_id").references(Players.id)
    val status = enumerationByName("status", 50, CallUpStatus::class).default(CallUpStatus.CALLED_UP)
    val notes = text("notes").nullable()
    // True for the Starting XI at kickoff (StartMatchUseCase), false for the bench. Meaningless
    // before kickoff.
    val isStarting = bool("is_starting").default(false)
    // V25: the position this player holds on the pitch (0 = goalkeeper, then defence, midfield,
    // attack -- see Formation.kt), as the coach last arranged it. Null for the bench.
    val pitchSlot = integer("pitch_slot").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_match_player", matchId, playerId)
    }
}

object MatchEvents : Table("match_events") {
    val id = uuid("id")
    val matchId = uuid("match_id").references(Matches.id, onDelete = ReferenceOption.CASCADE)
    val type = enumerationByName("type", 50, EventType::class)
    val minute = integer("minute")
    val playerId = uuid("player_id").references(Players.id).nullable()
    val opponentPlayerName = varchar("opponent_player_name", 255).nullable()
    val isOpponent = bool("is_opponent").default(false)
    val createdAt = timestamp("created_at")
    // 1 or 2 -- the half the event happened in (see MatchEvent.period). Null for older events.
    val period = integer("period").nullable()
    // For an ASSIST, the GOAL it belongs to (see MatchEvent.relatedEventId). FK declared in V8.
    val relatedEventId = uuid("related_event_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

// Each arrangement of the pitch during a game: at kickoff, then every formation change or position
// swap (V26, see MatchLineupChange). [slots] is JSON, slot -> player id.
object MatchLineupChanges : Table("match_lineup_changes") {
    val id = uuid("id")
    val matchId = uuid("match_id").references(Matches.id, onDelete = ReferenceOption.CASCADE)
    val minute = integer("minute")
    val period = integer("period")
    val formation = varchar("formation", 20)
    val slots = text("slots")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

// Per-player stats for a FINISHED match, computed once at full time (see MatchPlayerStats in
// Models.kt). One row per called-up player; `rating` is set later by the coach's match report.
object MatchPlayerStatsTable : Table("match_player_stats") {
    val id = uuid("id")
    val matchId = uuid("match_id").references(Matches.id, onDelete = ReferenceOption.CASCADE)
    val playerId = uuid("player_id").references(Players.id)
    val started = bool("started")
    val minutesPlayed = integer("minutes_played")
    val goals = integer("goals").default(0)
    val assists = integer("assists").default(0)
    val ownGoals = integer("own_goals").default(0)
    val yellowCards = integer("yellow_cards").default(0)
    val redCards = integer("red_cards").default(0)
    val rating = integer("rating").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_match_player_stats", matchId, playerId)
    }
}

// The coach's post-match report (see MatchReport in Models.kt). At most one per match; every text
// column is optional free text. completed_at null means the report is still pending.
object MatchReports : Table("match_reports") {
    val id = uuid("id")
    val matchId = uuid("match_id").references(Matches.id, onDelete = ReferenceOption.CASCADE).uniqueIndex("unique_match_report")
    val tactic = text("tactic").nullable()
    val positiveAspects = text("positive_aspects").nullable()
    val lessPositiveAspects = text("less_positive_aspects").nullable()
    val aspectsToImprove = text("aspects_to_improve").nullable()
    val teamNotes = text("team_notes").nullable()
    val opponentTactic = text("opponent_tactic").nullable()
    val opponentStrengths = text("opponent_strengths").nullable()
    val opponentWeaknesses = text("opponent_weaknesses").nullable()
    val opponentBestPlayers = text("opponent_best_players").nullable()
    val opponentAdditionalInfo = text("opponent_additional_info").nullable()
    val completedAt = timestamp("completed_at").nullable()
    val updatedAt = timestamp("updated_at")
    val updatedBy = uuid("updated_by").references(Users.id)

    override val primaryKey = PrimaryKey(id)
}

// A club's overrides for `ConfigurablePermission` (see Models.kt). No row for a given
// (club_id, permission) pair means "use ConfigurablePermission.defaultMinAgeGroupRole" -- a row exists only
// once a coordinator/admin has actually changed that action away from its default. min_team_role itself
// being NULL is a real, distinct value (not "no override"): it means "coordinator-only", i.e. the
// age-group-staff path is disabled entirely for that action.
object ClubPermissionOverrides : Table("club_permission_overrides") {
    val id = uuid("id")
    val clubId = uuid("club_id").references(Clubs.id)
    val permission = enumerationByName("permission", 50, ConfigurablePermission::class)
    val minAgeGroupRole = enumerationByName("min_team_role", 50, AgeGroupRole::class).nullable()
    // Club-level actions only (ConfigurablePermission.isClubLevel): COORDINATOR or CLUB_ADMIN. See V22.
    val minClubRole = enumerationByName("min_club_role", 50, ClubRole::class).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_club_permission", clubId, permission)
    }
}

// An age group's training plans (design 40): one row per season / month / week plan. See
// V15__training_plans.sql. The lists are JSON text, read and written with their plan.
object TrainingPlansTable : Table("training_plans") {
    val id = uuid("id")
    val ageGroupId = uuid("age_group_id").references(AgeGroups.id)
    val level = enumerationByName("level", 10, TrainingPlanLevel::class)
    val periodStart = date("period_start")
    val focus = varchar("focus", 255).nullable()
    val objectives = text("objectives")
    val notes = text("notes").nullable()
    val intensity = enumerationByName("intensity", 10, TrainingIntensity::class).nullable()
    val phases = text("phases")
    val updatedAt = timestamp("updated_at")
    val updatedBy = uuid("updated_by").references(Users.id)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_training_plan_period", ageGroupId, level, periodStart)
    }
}

// An age group's training sessions (design 39). See V17__training_sessions.sql.
object TrainingSessionsTable : Table("training_sessions") {
    val id = uuid("id")
    val ageGroupId = uuid("age_group_id").references(AgeGroups.id)
    val date = date("date")
    val startTime = time("start_time")
    val endTime = time("end_time")
    val location = varchar("location", 500).nullable()
    val notes = text("notes").nullable()
    val cancelledAt = timestamp("cancelled_at").nullable()
    val cancelledBy = uuid("cancelled_by").references(Users.id).nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_training_session_time", ageGroupId, date, startTime)
    }
}

// An age group's exercise library and each session's exercises, in order (design 39h). See V18.
object TrainingExercisesTable : Table("training_exercises") {
    val id = uuid("id")
    val ageGroupId = uuid("age_group_id").references(AgeGroups.id)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val durationMinutes = integer("duration_minutes").nullable()
    val objectives = text("objectives").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object TrainingSessionExercisesTable : Table("training_session_exercises") {
    val sessionId = uuid("session_id").references(TrainingSessionsTable.id)
    val exerciseId = uuid("exercise_id").references(TrainingExercisesTable.id)
    val position = integer("position")

    override val primaryKey = PrimaryKey(sessionId, exerciseId)
}

// Player ratings of a training session, in tenths (design 39g). See V19.
object TrainingRatingsTable : Table("training_ratings") {
    val sessionId = uuid("session_id").references(TrainingSessionsTable.id)
    val playerId = uuid("player_id").references(Players.id)
    val ratingTenths = integer("rating_tenths")
    val updatedAt = timestamp("updated_at")
    val updatedBy = uuid("updated_by").references(Users.id)

    override val primaryKey = PrimaryKey(sessionId, playerId)
}
