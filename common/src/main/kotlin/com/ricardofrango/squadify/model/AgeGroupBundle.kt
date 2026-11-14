package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

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
    val matches: List<BundleMatch>
)

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
    val logoUrl: String?
)

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
    val emergencyContact: String?
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
    val notes: String?
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
    val report: BundleReport?
)

@Serializable
data class BundleCallUp(
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
    val relatedEventId: String?
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
    val matches: Int
)
