package com.ricardofrango.squadify.domain.access

import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.Team

/**
 * Where a game action happens: the club (a coach's space, on the coach server) and, when known, the
 * team or age group it's about.
 */
data class GameScope(val clubId: String, val teamId: String? = null, val ageGroupId: String? = null)

val Match.scope: GameScope get() = GameScope(clubId, teamId = teamId)
val Team.scope: GameScope get() = GameScope(clubId, teamId = id)

/**
 * Who may do what with games: matches, call-ups, the live game, the record and report, and stats.
 * The game use cases ask this instead of checking club roles themselves, so they can be shared by
 * the clubs server and the coach server (TECHNICAL_DECISIONS §72), each providing its own policy:
 * the clubs server its roles and configurable permissions ([ClubAccessPolicy]), the coach server its
 * space membership and plan.
 *
 * Every `require*` throws [ForbiddenException] when the caller isn't allowed; [allowed] turns one
 * into a yes/no for a screen's flags.
 */
interface AccessPolicy {
    /** Belongs to the club: the live score and events, and a player's season stats. */
    suspend fun requireMember(userId: String, clubId: String)

    /** Sees a team's or an age group's games: its matches, details, season record and full stats. */
    suspend fun requireViewGames(userId: String, scope: GameScope)

    /** Creates, edits and deletes matches, and writes the match report. */
    suspend fun requireManageMatch(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    /** Saves a match's call-up. */
    suspend fun requireManageCallUps(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    /** Runs the live game (kickoff to full time, events, the pitch) and enters the record afterwards. */
    suspend fun requireManageLiveMatch(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    /** Reads the match report and players' ratings. */
    suspend fun requireSeeRatings(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    companion object {
        const val DEFAULT_MESSAGE = "Insufficient permissions to perform this action"
    }
}

/** A `require*` check as a yes/no, for the flags a screen shows. */
suspend fun allowed(check: suspend () -> Unit): Boolean = try {
    check()
    true
} catch (e: ForbiddenException) {
    false
}
