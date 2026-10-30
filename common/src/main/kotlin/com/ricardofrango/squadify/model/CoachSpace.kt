package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

// Squadify for Coaches (REQUIREMENTS §5c, TECHNICAL_DECISIONS §72): a coach's space is a club row the
// coach owns, never shown as a club. These are the coach server's first-setup request and responses.

/**
 * The first setup, right after signing up: the space's name and sport, and its first age group and
 * team. The season is picked from the date by the server.
 */
@Serializable
data class SetUpSpaceRequest(
    val name: String,
    val sport: SportType,
    val ageGroup: AgeGroupItemRequest,
    val teamName: String,
    // Null: the age group's team size.
    val teamSize: Int? = null
)

/** What the first setup created. */
@Serializable
data class SpaceSetup(
    val space: Club,
    val season: Season,
    val ageGroup: AgeGroup,
    val team: Team
)

/**
 * A code the owner shares so someone joins an age group of the space as staff (REQUIREMENTS §5c).
 * Single-use, and valid until [expiresAt]. [code] is shown as "K7Q-4MZ"; it's accepted with or
 * without the dash, in any case.
 */
@Serializable
data class StaffInvite(
    val code: String,
    val ageGroupId: String,
    val ageGroupName: String,
    val createdAt: Instant,
    val expiresAt: Instant
)

/**
 * One space the signed-in coach is in, with the age groups they have there: every one in the space they
 * own, the ones they're staff on in someone else's (REQUIREMENTS §5c).
 */
@Serializable
data class SpaceAgeGroups(
    val space: Club,
    val isOwner: Boolean,
    val ageGroups: List<AgeGroup>
)

/** A space the signed-in coach belongs to, and whether they own it. */
@Serializable
data class CoachSpace(
    val space: Club,
    val isOwner: Boolean
)
