package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable

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

/** The space the signed-in coach belongs to, and whether they own it. */
@Serializable
data class CoachSpace(
    val space: Club,
    val isOwner: Boolean
)
