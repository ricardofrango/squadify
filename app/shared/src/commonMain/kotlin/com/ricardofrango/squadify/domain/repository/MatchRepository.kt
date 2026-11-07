package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.domain.service.SocketEvent
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
import com.ricardofrango.squadify.model.SaveMatchRecordRequest
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.StartMatchRequest
import com.ricardofrango.squadify.model.SetMatchLineupRequest
import com.ricardofrango.squadify.model.MatchLineup
import com.ricardofrango.squadify.model.UpdateMatchRequest
import com.ricardofrango.squadify.model.MatchListStatus
import com.ricardofrango.squadify.model.MatchesPage
import com.ricardofrango.squadify.model.Paging
import kotlinx.coroutines.flow.Flow

interface MatchRepository {
    suspend fun createMatch(request: CreateMatchRequest): Match
    // One page of the club Matches tab's [status] list, narrowed on the server.
    suspend fun getMatchesPage(
        clubId: String,
        status: MatchListStatus,
        seasonId: String?,
        ageGroupId: String?,
        teamId: String?,
        offset: Int,
        limit: Int = Paging.PAGE_SIZE
    ): MatchesPage
    suspend fun getMatchDetails(matchId: String): MatchDetails
    suspend fun updateMatch(matchId: String, request: UpdateMatchRequest)
    suspend fun deleteMatch(matchId: String)
    suspend fun updateMatchStatus(matchId: String, status: MatchStatus)
    suspend fun startMatch(matchId: String, request: StartMatchRequest)
    suspend fun setLineup(matchId: String, request: SetMatchLineupRequest): MatchLineup
    suspend fun pauseMatch(matchId: String)
    // Takes the game over from whoever manages it (Match.liveManagerUserId).
    suspend fun takeOverLiveMatch(matchId: String)
    suspend fun resumeMatch(matchId: String)
    suspend fun setMatchMinute(matchId: String, minute: Int)
    suspend fun manageCallUps(matchId: String, playerIds: List<String>)
    // The post-match report for a FINISHED match: text, each called-up player's stats and rating.
    suspend fun getMatchReport(matchId: String): MatchReportDetails
    suspend fun saveMatchReport(matchId: String, request: SaveMatchReportRequest): MatchReport
    // After the game: its goals and cards corrected, or a game not followed live entered.
    suspend fun saveMatchRecord(matchId: String, request: SaveMatchRecordRequest): Match

    suspend fun getMatchEvents(matchId: String): List<MatchEvent>
    suspend fun getMatchScore(matchId: String): MatchScore
    suspend fun addMatchEvent(matchId: String, request: CreateMatchEventRequest): MatchEvent
    suspend fun deleteMatchEvent(matchId: String, eventId: String)
    // Cold: opens a WebSocket to `/matches/{matchId}/live` when collected and closes it when the
    // collector cancels: [SocketEvent.Open] once it's open, then its updates; it ends (or throws) when
    // the socket drops. Reopening is ObserveLiveMatchUseCase's (keepConnected).
    fun observeLiveMatch(matchId: String): Flow<SocketEvent<LiveMatchUpdate>>
    // Cold: opens a WebSocket to `/clubs/{clubId}/matches/live` when collected and closes it when the
    // collector cancels -- mirrors observeLiveMatch but for the whole club's fixture list rather than
    // one match's own event log. See ClubDetailsViewModel for the connect-on-tab-enter/disconnect-on-
    // tab-leave lifecycle this is used with. Reopened on a drop by ObserveClubMatchesUseCase.
    fun observeClubMatches(clubId: String): Flow<SocketEvent<ClubMatchesUpdate>>
}
