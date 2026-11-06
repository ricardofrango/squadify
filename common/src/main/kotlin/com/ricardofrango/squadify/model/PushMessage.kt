package com.ricardofrango.squadify.model

/**
 * A push notification's whole content, sent as FCM *data only* (string keys and values): the app always
 * draws the notification itself, in the foreground or not, so a match's pushes can be one chat-style
 * notification per match (TECHNICAL_DECISIONS.md §56). The server builds these and the app reads them,
 * so both use [toData] / [fromData], like [PushLink].
 *
 * - [bodyKey] / [bodyArgs]: the text, a string resource name and its arguments, resolved in the
 *   device's language by the app (§27).
 * - [imageUrl]: a public picture (§57). On a match push, the line's sender's picture; otherwise shown
 *   on the right (the club's logo).
 * - [thread]: set on a match's pushes, which the app gathers into one notification for that match.
 */
data class PushMessage(
    val link: PushLink,
    val channelId: String,
    val bodyKey: String,
    val bodyArgs: List<String> = emptyList(),
    val imageUrl: String? = null,
    val thread: MatchThread? = null
) {
    /**
     * One line in a match's notification, titled "[teamName] vs [opponentName]". [senderName] is who the
     * line is from: our team, our scorer, or the opponent ([sender]); [imageUrl] is their picture.
     * [teamImageUrl] (our club's logo) and [opponentImageUrl] are the notification's own picture, our
     * club in front of the opponent, with the app's badge (the developer, 2026-11-06); filled in by the
     * push service, each null without one.
     */
    data class MatchThread(
        val teamName: String,
        val opponentName: String,
        val sender: Sender,
        val senderName: String,
        val teamImageUrl: String? = null,
        val opponentImageUrl: String? = null
    )

    enum class Sender { TEAM, SCORER, OPPONENT }

    fun toData(): Map<String, String> = buildMap {
        putAll(link.toData())
        put(KEY_CHANNEL, channelId)
        put(KEY_BODY, bodyKey)
        put(KEY_BODY_ARG_COUNT, bodyArgs.size.toString())
        bodyArgs.forEachIndexed { i, arg -> put("$KEY_BODY_ARG$i", arg) }
        imageUrl?.let { put(KEY_IMAGE, it) }
        thread?.let {
            put(KEY_THREAD_TEAM, it.teamName)
            put(KEY_THREAD_OPPONENT, it.opponentName)
            put(KEY_THREAD_SENDER, it.sender.name)
            put(KEY_THREAD_SENDER_NAME, it.senderName)
            it.teamImageUrl?.let { url -> put(KEY_THREAD_TEAM_IMAGE, url) }
            it.opponentImageUrl?.let { url -> put(KEY_THREAD_OPPONENT_IMAGE, url) }
        }
    }

    companion object {
        const val KEY_CHANNEL = "squadifyChannel"
        const val KEY_BODY = "squadifyBody"
        const val KEY_BODY_ARG_COUNT = "squadifyBodyArgs"
        const val KEY_BODY_ARG = "squadifyBodyArg"
        const val KEY_IMAGE = "squadifyImage"
        const val KEY_THREAD_TEAM = "squadifyThreadTeam"
        const val KEY_THREAD_OPPONENT = "squadifyThreadOpponent"
        const val KEY_THREAD_SENDER = "squadifyThreadSender"
        const val KEY_THREAD_SENDER_NAME = "squadifyThreadSenderName"
        const val KEY_THREAD_TEAM_IMAGE = "squadifyThreadTeamImage"
        const val KEY_THREAD_OPPONENT_IMAGE = "squadifyThreadOpponentImage"

        /** Null when it isn't one of ours: no link or no text (an older server, or a Firebase console test). */
        fun fromData(data: Map<String, String?>): PushMessage? {
            fun value(key: String) = data[key]?.takeIf { it.isNotBlank() }
            val link = PushLink.fromData(data) ?: return null
            val bodyKey = value(KEY_BODY) ?: return null
            val args = (0 until (value(KEY_BODY_ARG_COUNT)?.toIntOrNull() ?: 0)).map { data["$KEY_BODY_ARG$it"].orEmpty() }
            val thread = run {
                val team = value(KEY_THREAD_TEAM) ?: return@run null
                val opponent = value(KEY_THREAD_OPPONENT) ?: return@run null
                val sender = value(KEY_THREAD_SENDER)?.let { s -> Sender.entries.firstOrNull { it.name == s } } ?: Sender.TEAM
                MatchThread(team, opponent, sender, value(KEY_THREAD_SENDER_NAME) ?: team, value(KEY_THREAD_TEAM_IMAGE), value(KEY_THREAD_OPPONENT_IMAGE))
            }
            return PushMessage(link, value(KEY_CHANNEL) ?: "default", bodyKey, args, value(KEY_IMAGE), thread)
        }
    }
}
