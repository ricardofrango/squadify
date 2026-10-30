package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.domain.service.GoalPush
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.PushLink

/**
 * The coach app has no push yet: it comes with its own Firebase app (TECHNICAL_DECISIONS §72, "Shared
 * services"). Until then the shared use cases' pushes go nowhere.
 */
object NoPushNotifications : PushNotificationService {
    override suspend fun sendClubMembershipAdded(tokens: List<String>, link: PushLink, clubName: String) = Unit
    override suspend fun sendAgeGroupStaffAdded(tokens: List<String>, link: PushLink, ageGroupName: String, clubName: String) = Unit
    override suspend fun sendMatchScheduled(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) = Unit
    override suspend fun sendMatchRescheduled(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) = Unit
    override suspend fun sendMatchCancelled(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) = Unit
    override suspend fun sendMatchKickedOff(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) = Unit
    override suspend fun sendHalfTime(tokens: List<String>, link: PushLink, teamName: String, opponentName: String, score: MatchScore) = Unit
    override suspend fun sendSecondHalfStarted(tokens: List<String>, link: PushLink, teamName: String, opponentName: String) = Unit
    override suspend fun sendMatchFinished(tokens: List<String>, link: PushLink, teamName: String, opponentName: String, score: MatchScore) = Unit
    override suspend fun sendGoalScored(tokens: List<String>, link: PushLink, teamName: String, opponentName: String, score: MatchScore, goal: GoalPush) = Unit
    override suspend fun sendTeamCreated(tokens: List<String>, link: PushLink, teamNames: List<String>, ageGroupName: String, clubName: String) = Unit
}
