package com.ricardofrango.squadify.presentation.ui.matchreport

import com.ricardofrango.squadify.model.MatchPlayerRatingInput
import com.ricardofrango.squadify.model.MatchPlayerStatsWithPlayer
import com.ricardofrango.squadify.model.MatchReport
import com.ricardofrango.squadify.model.MatchReportDetails
import com.ricardofrango.squadify.model.SaveMatchReportRequest

/** The report's nine free-text fields, as edited. Empty string means "not written". */
data class ReportFields(
    val tactic: String = "",
    val positiveAspects: String = "",
    val lessPositiveAspects: String = "",
    val aspectsToImprove: String = "",
    val teamNotes: String = "",
    val opponentStrengths: String = "",
    val opponentWeaknesses: String = "",
    val opponentBestPlayers: String = "",
    val opponentAdditionalInfo: String = ""
) {
    val teamFilled: Int
        get() = listOf(tactic, positiveAspects, lessPositiveAspects, aspectsToImprove, teamNotes).count { it.isNotBlank() }
    val opponentFilled: Int
        get() = listOf(opponentStrengths, opponentWeaknesses, opponentBestPlayers, opponentAdditionalInfo).count { it.isNotBlank() }
    val isEmpty: Boolean get() = teamFilled == 0 && opponentFilled == 0

    companion object {
        fun from(report: MatchReport?) = ReportFields(
            tactic = report?.tactic.orEmpty(),
            positiveAspects = report?.positiveAspects.orEmpty(),
            lessPositiveAspects = report?.lessPositiveAspects.orEmpty(),
            aspectsToImprove = report?.aspectsToImprove.orEmpty(),
            teamNotes = report?.teamNotes.orEmpty(),
            opponentStrengths = report?.opponentStrengths.orEmpty(),
            opponentWeaknesses = report?.opponentWeaknesses.orEmpty(),
            opponentBestPlayers = report?.opponentBestPlayers.orEmpty(),
            opponentAdditionalInfo = report?.opponentAdditionalInfo.orEmpty()
        )
    }
}

enum class ReportTab { TEAM, OPPONENT, PLAYERS }

enum class SaveAction { DRAFT, COMPLETE }

/** Transient confirmation after a successful save (design 24e: toast "Draft saved"). */
enum class ReportToast { DRAFT_SAVED, REPORT_COMPLETED, REPORT_UPDATED }

sealed class MatchReportUiState {
    data object Loading : MatchReportUiState()
    data class Error(val message: String) : MatchReportUiState()

    /** 403 on load: a player, parent, or staff member from another age group (design 26f). */
    data object NoPermission : MatchReportUiState()

    data class Idle(
        val details: MatchReportDetails,
        // Editor (tabs, inputs, action bar) vs the read view (one scroll). Only possible with canEdit.
        val editing: Boolean,
        val originalFields: ReportFields,
        val fields: ReportFields,
        val originalRatings: Map<String, Int?>,
        val ratings: Map<String, Int?>,
        val tab: ReportTab,
        // The player whose 0-10 grid is open in the portrait editor, if any.
        val openRatingFor: String? = null,
        val readSort: ReportPlayerSort = ReportPlayerSort.RATING,
        val saving: SaveAction? = null,
        // The last save failed: header chip "Not saved" + a retry snackbar until a save succeeds.
        val failedAction: SaveAction? = null,
        val toast: ReportToast? = null,
        // A transient, localized message for a failed save that isn't a plain network error (e.g. 403).
        val saveErrorMessage: String? = null
    ) : MatchReportUiState() {
        val players: List<MatchPlayerStatsWithPlayer> get() = editorOrder(details.playerStats)
        val hasStats: Boolean get() = details.playerStats.isNotEmpty()
        val unratedCount: Int get() = players.count { ratings[it.player.id] == null }
        val isCompleted: Boolean get() = details.report?.completedAt != null
        val isDirty: Boolean get() = fields != originalFields || ratings != originalRatings
        val hasContent: Boolean get() = !fields.isEmpty || ratings.values.any { it != null }
        val isBrandNew: Boolean get() = details.report == null && !isDirty
        val canSaveDraft: Boolean get() = saving == null && isDirty
        // Completing an empty report is blocked (26c); once completed, the button re-saves only changes.
        val canComplete: Boolean get() = saving == null && hasContent && (isDirty || !isCompleted)

        fun toRequest(complete: Boolean) = SaveMatchReportRequest(
            tactic = fields.tactic,
            positiveAspects = fields.positiveAspects,
            lessPositiveAspects = fields.lessPositiveAspects,
            aspectsToImprove = fields.aspectsToImprove,
            teamNotes = fields.teamNotes,
            opponentStrengths = fields.opponentStrengths,
            opponentWeaknesses = fields.opponentWeaknesses,
            opponentBestPlayers = fields.opponentBestPlayers,
            opponentAdditionalInfo = fields.opponentAdditionalInfo,
            ratings = players.map { MatchPlayerRatingInput(it.player.id, ratings[it.player.id]) },
            complete = complete
        )
    }
}
