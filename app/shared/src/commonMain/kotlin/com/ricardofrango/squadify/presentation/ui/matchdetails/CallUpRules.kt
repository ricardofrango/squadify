package com.ricardofrango.squadify.presentation.ui.matchdetails

import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchStatus

/** What Match details' call-up section shows. */
enum class CallUpSection {
    /** The call-up being built, plus the available players: for those who manage call-ups. */
    EDITOR,
    /** The saved call-up, read-only. */
    READ_ONLY,
    /** Only "the call-up isn't ready yet": for someone who could start the game, but not before it is. */
    NOT_READY,
    /** Nothing about players: for someone who neither manages call-ups nor starts the game. */
    HIDDEN
}

/** Match details' bottom button. */
enum class MatchDetailsButton { SAVE_CALL_UP_DISABLED, SAVE_CALL_UP, START_MATCH, ENTER_LIVE_MATCH, NONE }

data class CallUpView(val section: CallUpSection, val button: MatchDetailsButton)

/** The fewest players a call-up needs: the match's team size (how many start). */
fun MatchDetails.minimumCallUp(): Int = match.teamSize

/**
 * What the call-up section and the bottom button show (the developer's rules, 2026-10-23). The
 * call-up is a draft until it's saved: [selectedIds] is what's on screen, [savedIds] what the server has.
 * - Managing call-ups: the editor. The button is "Save call-up" -- disabled below [minimum] players --
 *   while the draft differs from the saved one, and "Start match" once they match (with the right
 *   to start it), so going back to the saved call-up brings "Start match" back.
 * - Starting the game only: the saved call-up and "Start match" when it's ready (at least [minimum]),
 *   else just a "not ready" message.
 * - Neither: no players at all.
 */
fun callUpView(
    status: MatchStatus,
    canManageCallUps: Boolean,
    canManageLiveMatch: Boolean,
    savedIds: Set<String>,
    selectedIds: Set<String>,
    minimum: Int
): CallUpView {
    val ready = savedIds.size >= minimum
    val seesPlayers = canManageCallUps || (canManageLiveMatch && ready)
    return when (status) {
        MatchStatus.SCHEDULED -> when {
            canManageCallUps -> CallUpView(
                CallUpSection.EDITOR,
                when {
                    selectedIds.size < minimum -> MatchDetailsButton.SAVE_CALL_UP_DISABLED
                    selectedIds != savedIds -> MatchDetailsButton.SAVE_CALL_UP
                    canManageLiveMatch -> MatchDetailsButton.START_MATCH
                    else -> MatchDetailsButton.NONE
                }
            )
            canManageLiveMatch && ready -> CallUpView(CallUpSection.READ_ONLY, MatchDetailsButton.START_MATCH)
            canManageLiveMatch -> CallUpView(CallUpSection.NOT_READY, MatchDetailsButton.NONE)
            else -> CallUpView(CallUpSection.HIDDEN, MatchDetailsButton.NONE)
        }
        MatchStatus.LIVE, MatchStatus.HALF_TIME -> CallUpView(
            if (seesPlayers) CallUpSection.READ_ONLY else CallUpSection.HIDDEN,
            if (canManageLiveMatch) MatchDetailsButton.ENTER_LIVE_MATCH else MatchDetailsButton.NONE
        )
        // Finished or cancelled: a record, never edited.
        else -> CallUpView(if (canManageCallUps || canManageLiveMatch) CallUpSection.READ_ONLY else CallUpSection.HIDDEN, MatchDetailsButton.NONE)
    }
}

/** [callUpView] for this screen's state. */
fun MatchDetailsUiState.Idle.callUpView(): CallUpView = callUpView(
    status = details.match.status,
    canManageCallUps = details.canManageCallUps,
    canManageLiveMatch = details.canManageLiveMatch,
    savedIds = savedIds,
    selectedIds = selectedIds,
    minimum = details.minimumCallUp()
)
