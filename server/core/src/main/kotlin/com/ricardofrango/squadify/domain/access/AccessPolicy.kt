package com.ricardofrango.squadify.domain.access

import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.PlanLimitException
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
 * Who may do what with games (matches, call-ups, the live game, the record and report, and stats) and
 * with the structure they're played in (age groups, teams, players and opponents). The use cases ask
 * this instead of checking club roles themselves, so they can be shared by the clubs server and the
 * coach server (TECHNICAL_DECISIONS §72), each providing its own policy:
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

    /** Changes the club's own details, such as its logo. */
    suspend fun requireEditClub(userId: String, clubId: String, errorMessage: String = DEFAULT_MESSAGE)

    /** Sees the club's lists: its age groups, teams and players, narrowed by [visibleAgeGroupIds]. */
    suspend fun requireViewClub(userId: String, clubId: String)

    /**
     * The age groups of [clubId] the caller sees in the club's lists, or null for all of them. Only
     * narrows lists: each age group's own screen asks [requireViewGames].
     */
    suspend fun visibleAgeGroupIds(userId: String, clubId: String): Set<String>?

    /** Sees what only staff see in an age group (not its players or parents): who could join its pool. */
    suspend fun requireStaff(userId: String, scope: GameScope)

    /** Opens the members list from an age group's or team's staff. */
    suspend fun requireViewMembers(userId: String, clubId: String)

    /** Creates age groups. */
    suspend fun requireCreateAgeGroups(userId: String, clubId: String, errorMessage: String = DEFAULT_MESSAGE)

    /** Edits an age group: its name, birth years and team size. */
    suspend fun requireEditAgeGroup(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    /** Adds staff to an age group. */
    suspend fun requireManageStaff(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    /** Creates and deletes an age group's teams. */
    suspend fun requireManageTeams(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    /** Edits a team: its name, code and team size. */
    suspend fun requireEditTeam(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    /** Creates players, in the club or from an age group. */
    suspend fun requireCreatePlayers(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    /** Edits a player. */
    suspend fun requireEditPlayer(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    /** Adds players to an age group's pool and removes them from it. */
    suspend fun requireManagePool(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    /** Sees the club's opponents, e.g. to pick one when scheduling a match. */
    suspend fun requireViewOpponents(userId: String, scope: GameScope)

    /** Creates opponents. */
    suspend fun requireCreateOpponent(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    /** Edits an opponent. */
    suspend fun requireEditOpponent(userId: String, scope: GameScope, errorMessage: String = DEFAULT_MESSAGE)

    /**
     * The club's plan includes stats: season records, team leaders, player season stats, full stats and
     * a match's player lines. Only the coach server has plans (Basic and Pro); the clubs server always
     * allows. Asked after the read check of the screen's own data. Throws [PlanLimitException].
     */
    suspend fun requireStatsInPlan(clubId: String) {}

    /** The club's plan has room for [count] more age groups in [seasonId] (the coach server's plans). */
    suspend fun requireRoomForAgeGroups(clubId: String, seasonId: String, count: Int) {}

    companion object {
        const val DEFAULT_MESSAGE = "Insufficient permissions to perform this action"
    }
}

/** A `require*` check as a yes/no, for the flags a screen shows. Not on the plan is a no too. */
suspend fun allowed(check: suspend () -> Unit): Boolean = try {
    check()
    true
} catch (e: ForbiddenException) {
    false
} catch (e: PlanLimitException) {
    false
}
