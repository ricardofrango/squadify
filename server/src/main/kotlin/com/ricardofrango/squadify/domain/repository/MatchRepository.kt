package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.*

interface MatchRepository {
    suspend fun getMatchById(id: String): Match?
    suspend fun getMatchesByTeam(teamId: String): List<Match>
    suspend fun getMatchesByClub(clubId: String): List<Match>
    suspend fun createMatch(match: Match): Match
    suspend fun updateMatch(match: Match): Boolean
    // Call-ups and events must be cleared first (DeleteMatchUseCase does this) -- there's no ON
    // DELETE CASCADE on their matchId foreign keys, so deleting a match with either still attached
    // fails the FK constraint.
    suspend fun deleteMatch(matchId: String): Boolean
    suspend fun updateMatchStatus(matchId: String, status: MatchStatus): Boolean
    // Kicks off: sets isStarting=true on [startingPlayerIds]' call-ups (false on every other call-up
    // of this match), and sets the match's status to LIVE and its half length -- all in one write.
    suspend fun startMatch(matchId: String, startingPlayerIds: List<String>, halfLengthMinutes: Int): Boolean
    // Records the wall-clock instant the second half resumed -- see UpdateMatchStatusUseCase, which
    // calls this on a HALF_TIME -> LIVE transition so the client can derive the running clock from it.
    suspend fun markSecondHalfStarted(matchId: String, at: kotlin.time.Instant): Boolean
    // Record the match-clock minute the first half / the match ended on (Match.firstHalfEndMinute /
    // fullTimeMinute) -- see UpdateMatchStatusUseCase's HALF_TIME and FINISHED transitions.
    suspend fun markFirstHalfEnded(matchId: String, minute: Int): Boolean
    suspend fun markFullTime(matchId: String, minute: Int): Boolean
    // markSecondHalfStarted / markFirstHalfEnded / markFullTime also clear pausedAt: ending or starting a
    // half ends any stoppage, so a clock left paused at the whistle doesn't carry into the next half.
    // Freezes the running clock for a mid-half stoppage -- see PauseMatchUseCase/Match.pausedAt.
    suspend fun pauseMatch(matchId: String, at: kotlin.time.Instant): Boolean
    // Clears pausedAt and writes the caller's already-shifted kickoffAt/secondHalfStartedAt (whichever
    // was driving the clock while paused) so the running clock continues from where it paused instead
    // of jumping forward by the paused duration -- see ResumeMatchUseCase, which computes the shift.
    suspend fun resumeMatch(matchId: String, kickoffAt: kotlin.time.Instant, secondHalfStartedAt: kotlin.time.Instant?): Boolean
    // Writes the caller's already-computed kickoffAt/secondHalfStartedAt without touching pausedAt or
    // status -- unlike resumeMatch, this must work while paused (correcting a frozen display) without
    // side-effecting a resume. See SetMatchMinuteUseCase, which computes the shift.
    suspend fun adjustMatchClock(matchId: String, kickoffAt: kotlin.time.Instant, secondHalfStartedAt: kotlin.time.Instant?): Boolean

    suspend fun getCallUpsForMatch(matchId: String): List<MatchCallUpWithPlayer>
    suspend fun countCallUpsByPlayer(playerId: String): Long
    // Ids of every match the player was called up for.
    suspend fun getCallUpMatchIdsByPlayer(playerId: String): List<String>
    suspend fun manageCallUps(matchId: String, playerIds: List<String>): Boolean
    suspend fun getCallUpByMatchAndPlayer(matchId: String, playerId: String): MatchCallUp?
    suspend fun getUpcomingMatchesByClub(clubId: String, limit: Int): List<Match>
    suspend fun getUpcomingMatchesByPlayer(playerId: String, limit: Int): List<Match>
    // The opponents of the club's matches, by each one's latest match, newest first -- at most [limit].
    suspend fun getRecentOpponentIds(clubId: String, limit: Int): List<String>
}
