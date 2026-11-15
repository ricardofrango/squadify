package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * One age group copied out of a server (TECHNICAL_DECISIONS §81): the source's ids throughout, so the importing
 * server can map each record to its own copy and remember where it came from. Phase 1 carries the structure,
 * the players, the opponents used and the matches with their call-ups, line-ups, events and reports; training
 * and images follow in later phases.
 */
@Serializable
data class AgeGroupBundle(
    val sourceServer: String,
    val season: BundleSeason,
    val ageGroup: BundleAgeGroup,
    val teams: List<BundleTeam>,
    val players: List<BundlePlayer>,
    val ageGroupPlayers: List<BundleAgeGroupPlayer>,
    val opponents: List<BundleOpponent>,
    val matches: List<BundleMatch>,
    // Training (Pro in the coach app, TECHNICAL_DECISIONS §81): its exercises, sessions and plans.
    val exercises: List<BundleExercise> = emptyList(),
    val sessions: List<BundleSession> = emptyList(),
    val plans: List<TrainingPlan> = emptyList()
)

@Serializable
data class BundleExercise(
    val id: String,
    val name: String,
    val description: String?,
    val durationMinutes: Int?,
    val objectives: String?
)

/** A training session, its exercises in order (their ids), and the ratings in hundredths. */
@Serializable
data class BundleSession(
    val id: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val location: String?,
    val notes: String?,
    val cancelledAt: Instant?,
    val exerciseIds: List<String>,
    val ratings: List<BundleSessionRating>
)

@Serializable
data class BundleSessionRating(val playerId: String, val hundredths: Int)

@Serializable
data class BundleSeason(
    val id: String,
    val name: String,
    val type: SeasonType,
    val startDate: LocalDate,
    val endDate: LocalDate
)

@Serializable
data class BundleAgeGroup(
    val id: String,
    val name: String,
    val startYear: Int,
    val endYear: Int,
    val teamSize: Int?,
    val logoUrl: String?,
    // The crest's file (TECHNICAL_DECISIONS §81: images are copied as files), when the age group has one.
    val logo: BundleImage? = null
)

/** An image file, base64-encoded in the bundle (it's copied to the target's own storage). */
@Serializable
data class BundleImage(val contentType: String, val base64: String)

@Serializable
data class BundleTeam(
    val id: String,
    val name: String,
    val code: String?,
    val active: Boolean,
    val teamSize: Int?
)

@Serializable
data class BundlePlayer(
    val id: String,
    val firstName: String,
    val lastName: String,
    val birthDate: LocalDate,
    val shirtNumber: Int?,
    val photoUrl: String?,
    val active: Boolean,
    val position: PlayerPosition?,
    val heightCm: Int?,
    val weightKg: Int?,
    val emergencyContact: String?,
    val photo: BundleImage? = null
)

@Serializable
data class BundleAgeGroupPlayer(val playerId: String)

@Serializable
data class BundleOpponent(
    val id: String,
    val name: String,
    val shortName: String?,
    val logoUrl: String?,
    val stadiumName: String?,
    val stadiumAddress: String?,
    val contactEmail: String?,
    val contactPhone: String?,
    val notes: String?,
    val logo: BundleImage? = null
)

/** A match that isn't in progress (a live one is skipped, see §81). */
@Serializable
data class BundleMatch(
    val id: String,
    val teamId: String,
    val opponentId: String,
    val location: String?,
    val dateTime: Instant,
    val status: MatchStatus,
    val notes: String?,
    val matchType: MatchType?,
    val halves: Int,
    val venue: MatchVenue,
    val halfLengthMinutes: Int?,
    val kickoffAt: Instant?,
    val secondHalfStartedAt: Instant?,
    val firstHalfEndMinute: Int?,
    val fullTimeMinute: Int?,
    val formation: String?,
    val startingFormation: String?,
    val enteredAfterGame: Boolean,
    val sport: SportType,
    val teamSize: Int,
    val callUps: List<BundleCallUp>,
    val lineupChanges: List<MatchLineupChange>,
    val events: List<BundleEvent>,
    val ratings: List<BundlePlayerRating>,
    val report: BundleReport?
)

/** The coach's rating of a player in a finished match (MatchPlayerStats.rating), kept as the source has it. */
@Serializable
data class BundlePlayerRating(val playerId: String, val rating: Int?)

@Serializable
data class BundleCallUp(
    val id: String,
    val playerId: String,
    val status: CallUpStatus,
    val notes: String?,
    val isStarting: Boolean,
    val pitchSlot: Int?
)

@Serializable
data class BundleEvent(
    val id: String,
    val type: EventType,
    val minute: Int,
    val playerId: String?,
    val opponentPlayerName: String?,
    val isOpponent: Boolean,
    val period: Int?,
    val relatedEventId: String?,
    val createdAt: Instant
)

/** The coach's post-match report; its author is the source's user, mapped to a user the target knows, or null. */
@Serializable
data class BundleReport(
    val tactic: String?,
    val positiveAspects: String?,
    val lessPositiveAspects: String?,
    val aspectsToImprove: String?,
    val teamNotes: String?,
    val opponentTactic: String?,
    val opponentStrengths: String?,
    val opponentWeaknesses: String?,
    val opponentBestPlayers: String?,
    val opponentAdditionalInfo: String?,
    val completedAt: Instant?
)

/** The result of importing a bundle: how many records of each kind were created, and the ones already there. */
@Serializable
data class ImportAgeGroupResult(
    val ageGroupId: String,
    val teams: Int,
    val players: Int,
    val opponents: Int,
    val matches: Int,
    /** The source's training wasn't copied: the space's plan doesn't include training. The age group itself was. */
    val trainingSkipped: Boolean = false
)

/** A grant to read one of the caller's age groups from another server (TECHNICAL_DECISIONS §81). */
@Serializable
data class ExportGrantResponse(val token: String, val expiresAt: Instant)

/**
 * An age group that can be copied out of a server, for the importing app's list: its club's sport, and its season
 * (type and dates), which the importing server matches to its own season.
 */
@Serializable
data class ExportableAgeGroup(
    val id: String,
    val clubId: String,
    val clubName: String,
    val name: String,
    val startYear: Int,
    val endYear: Int,
    val seasonName: String,
    val sport: SportType,
    val seasonType: SeasonType,
    val seasonStartDate: LocalDate,
    val seasonEndDate: LocalDate
)

/** The importing server's request: copy [sourceAgeGroupId] from [sourceUrl] (using [grant]) into [clubId]. */
@Serializable
data class ImportAgeGroupRequest(
    val clubId: String,
    val sourceUrl: String,
    val sourceAgeGroupId: String,
    val grant: String
)

/** The importing app's request for the age groups a grant can copy from [sourceUrl] into [clubId]. */
@Serializable
data class ListSourceAgeGroupsRequest(val clubId: String, val sourceUrl: String, val grant: String)

/**
 * An age group the other server offers for import. [blocked]: why this club can't copy it now (null: it can). The
 * sport is the source's; [clashName] and the years are the age group already in the club that it clashes with.
 */
@Serializable
data class ImportableAgeGroup(
    val id: String,
    val clubName: String,
    val name: String,
    val startYear: Int,
    val endYear: Int,
    val seasonName: String,
    val sport: SportType,
    val blocked: ImportBlock? = null,
    val clashName: String? = null,
    val clashStartYear: Int? = null,
    val clashEndYear: Int? = null
)

/** Why an age group can't be copied into a club right now (shown in the import list, TECHNICAL_DECISIONS §81). */
@Serializable
enum class ImportBlock {
    /** This club already has its copy. */
    ALREADY_IMPORTED,

    /** Another sport: a futsal age group can't go into a football club. */
    OTHER_SPORT,

    /** Another season (other dates) than this club's: only the club's own season's age groups are copied in. */
    OTHER_SEASON,

    /** The club already has an age group for these birth years in the same season. */
    BIRTH_YEARS_TAKEN,

    /** The coach space's plan has no room for another age group this season. */
    NO_ROOM_IN_PLAN
}
