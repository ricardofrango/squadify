package com.ricardofrango.squadify.model

/**
 * Where tapping a push notification takes the user, sent as the FCM message's `data` (string keys and
 * values only). The most specific id wins: a match, then a team, then an age group, else the club.
 * [clubId] is always set, so the app can put the club's screen under the target (its colours, and
 * somewhere to go back to). The server builds these and the app reads them, so both use [toData] /
 * [fromData]. See TECHNICAL_DECISIONS.md §56.
 */
data class PushLink(
    val clubId: String,
    val ageGroupId: String? = null,
    val teamId: String? = null,
    val matchId: String? = null
) {
    fun toData(): Map<String, String> = buildMap {
        put(KEY_CLUB_ID, clubId)
        ageGroupId?.let { put(KEY_AGE_GROUP_ID, it) }
        teamId?.let { put(KEY_TEAM_ID, it) }
        matchId?.let { put(KEY_MATCH_ID, it) }
    }

    companion object {
        const val KEY_CLUB_ID = "squadifyClubId"
        const val KEY_AGE_GROUP_ID = "squadifyAgeGroupId"
        const val KEY_TEAM_ID = "squadifyTeamId"
        const val KEY_MATCH_ID = "squadifyMatchId"

        val KEYS = listOf(KEY_CLUB_ID, KEY_AGE_GROUP_ID, KEY_TEAM_ID, KEY_MATCH_ID)

        fun club(clubId: String) = PushLink(clubId)
        fun ageGroup(clubId: String, ageGroupId: String) = PushLink(clubId, ageGroupId = ageGroupId)
        fun team(clubId: String, teamId: String) = PushLink(clubId, teamId = teamId)
        fun match(clubId: String, matchId: String) = PushLink(clubId, matchId = matchId)

        /** Null without a club id: a push from an older server, or one sent by hand from the Firebase console. */
        fun fromData(data: Map<String, String?>): PushLink? {
            fun value(key: String) = data[key]?.takeIf { it.isNotBlank() }
            val clubId = value(KEY_CLUB_ID) ?: return null
            return PushLink(clubId, value(KEY_AGE_GROUP_ID), value(KEY_TEAM_ID), value(KEY_MATCH_ID))
        }
    }
}
