package com.ricardofrango.squadify.domain.service

import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.PushLink

/**
 * Best-effort push notifications (Firebase Cloud Messaging) for association events -- the push
 * equivalent of [EmailService]. Implementations must never throw: a failed send must never fail the
 * use case that triggered it, since the underlying database association already succeeded by the
 * time these are called. See TECHNICAL_DECISIONS.md.
 *
 * These method signatures deliberately carry plain data (names, scores), never pre-rendered message
 * text -- implementations localize by sending a body-localization *key* + those arguments (FCM's own
 * mechanism), letting the client render in its own device locale, rather than the server picking a
 * language. See TECHNICAL_DECISIONS.md §27.
 *
 * Every push also carries a [PushLink] -- what tapping it opens (the club, the age group, the team or
 * the match), sent as the message's data. See TECHNICAL_DECISIONS.md §56.
 */
interface PushNotificationService {
    /**
     * Sent to every device in [tokens] when an existing user is added to a club directly (no
     * registration needed) -- the push equivalent of [EmailService.sendClubMembershipAdded]. Callers
     * are responsible for only invoking this with a non-empty [tokens] list (i.e. only for users who
     * actually have at least one registered device).
     */
    suspend fun sendClubMembershipAdded(tokens: List<String>, link: PushLink, clubName: String)

    /**
     * Sent to every device in [tokens] when an existing user is added as staff/coach to an age group
     * -- the push equivalent of [EmailService.sendAgeGroupStaffAdded]. Same non-empty-[tokens]
     * contract as [sendClubMembershipAdded].
     */
    suspend fun sendAgeGroupStaffAdded(tokens: List<String>, link: PushLink, ageGroupName: String, clubName: String)

    /**
     * Match events, sent to every device in [tokens] -- unlike the association pushes above, these go
     * to an entire club's membership (see [com.ricardofrango.squadify.domain.usecase.
     * NotifyMatchEventUseCase]), not just the person directly affected, so [tokens] here can be much
     * larger. Same non-empty-[tokens] contract as [sendClubMembershipAdded].
     *
     * The first three are schedule changes (a fixture created, rescheduled, or cancelled); the rest
     * are live-match moments.
     */
    suspend fun sendMatchScheduled(tokens: List<String>, link: PushLink, teamName: String, opponentName: String)
    suspend fun sendMatchRescheduled(tokens: List<String>, link: PushLink, teamName: String, opponentName: String)
    suspend fun sendMatchCancelled(tokens: List<String>, link: PushLink, teamName: String, opponentName: String)
    suspend fun sendMatchKickedOff(tokens: List<String>, link: PushLink, teamName: String, opponentName: String)
    suspend fun sendHalfTime(tokens: List<String>, link: PushLink, teamName: String, opponentName: String, score: MatchScore)
    suspend fun sendSecondHalfStarted(tokens: List<String>, link: PushLink, teamName: String, opponentName: String)
    suspend fun sendMatchFinished(tokens: List<String>, link: PushLink, teamName: String, opponentName: String, score: MatchScore)
    /** [goal]: whose goal it was, our scorer's name, and the picture to show on the right. */
    suspend fun sendGoalScored(tokens: List<String>, link: PushLink, teamName: String, opponentName: String, score: MatchScore, goal: GoalPush = GoalPush())

    /**
     * Sent to every device in [tokens] when one or more teams are created under an age group -- like
     * the match schedule pushes above, every active club member gets this (a new team is club-wide
     * roster news), not just the age group's own staff/players/parents. [teamNames] covers both
     * [com.ricardofrango.squadify.domain.usecase.CreateTeamUseCase] (one team) and
     * [com.ricardofrango.squadify.domain.usecase.CreateTeamsUseCase] (a batch) with a single method,
     * rather than sending one push per team when several are created at once. Same non-empty-[tokens]
     * contract as [sendClubMembershipAdded].
     */
    suspend fun sendTeamCreated(tokens: List<String>, link: PushLink, teamNames: List<String>, ageGroupName: String, clubName: String)
}

/**
 * A goal push's details. [forUs]: our goal (a goal of ours, or an opponent's own goal) or theirs.
 * [scorerName]: our scorer, when the goal was ours and credited to a player. [imageUrl]: the scorer's
 * photo (ours) or the opponent's logo (theirs), shown on the right; null falls back to the club's logo.
 */
data class GoalPush(val forUs: Boolean = true, val scorerName: String? = null, val imageUrl: String? = null)
