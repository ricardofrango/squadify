package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.model.CreateMatchEventRequest
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.LiveMatchUpdate
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchReport
import com.ricardofrango.squadify.model.MatchReportDetails
import com.ricardofrango.squadify.model.SaveMatchReportRequest
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.StartMatchRequest
import com.ricardofrango.squadify.model.SetMatchLineupRequest
import com.ricardofrango.squadify.model.MatchLineup
import com.ricardofrango.squadify.model.UpdateMatchRequest
import kotlinx.coroutines.flow.Flow

interface MatchRepository {
    suspend fun createMatch(request: CreateMatchRequest): Match
    suspend fun getMatches(clubId: String): List<Match>
    suspend fun getMatchDetails(matchId: String): MatchDetails
    suspend fun updateMatch(matchId: String, request: UpdateMatchRequest)
    suspend fun deleteMatch(matchId: String)
    suspend fun updateMatchStatus(matchId: String, status: MatchStatus)
    suspend fun startMatch(matchId: String, request: StartMatchRequest)
    suspend fun setLineup(matchId: String, request: SetMatchLineupRequest): MatchLineup
    suspend fun pauseMatch(matchId: String)
    suspend fun resumeMatch(matchId: String)
    suspend fun setMatchMinute(matchId: String, minute: Int)
    suspend fun manageCallUps(matchId: String, playerIds: List<String>)
    // The post-match report for a FINISHED match: text, each called-up player's stats and rating.
    suspend fun getMatchReport(matchId: String): MatchReportDetails
    suspend fun saveMatchReport(matchId: String, request: SaveMatchReportRequest): MatchReport

    suspend fun getMatchEvents(matchId: String): List<MatchEvent>
    suspend fun getMatchScore(matchId: String): MatchScore
    suspend fun addMatchEvent(matchId: String, request: CreateMatchEventRequest): MatchEvent
    suspend fun deleteMatchEvent(matchId: String, eventId: String)
    // Cold: opens a WebSocket to `/matches/{matchId}/live` when collected and closes it when the
    // collector cancels. Reconnection on drop is the caller's job (see LiveMatchViewModel).
    fun observeLiveMatch(matchId: String): Flow<LiveMatchUpdate>
    // Cold: opens a WebSocket to `/clubs/{clubId}/matches/live` when collected and closes it when the
    // collector cancels -- mirrors observeLiveMatch but for the whole club's fixture list rather than
    // one match's own event log. See ClubDetailsViewModel for the connect-on-tab-enter/disconnect-on-
    // tab-leave lifecycle this is used with.
    fun observeClubMatches(clubId: String): Flow<ClubMatchesUpdate>
}
