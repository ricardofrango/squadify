package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.TrainingIntensity
import com.ricardofrango.squadify.model.TrainingPhase
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.presentation.ui.training.PlanDateError
import com.ricardofrango.squadify.presentation.ui.training.PlanForm
import com.ricardofrango.squadify.presentation.ui.training.copiedFrom
import com.ricardofrango.squadify.presentation.ui.training.hasTypedContent
import com.ricardofrango.squadify.presentation.ui.training.isValid
import com.ricardofrango.squadify.presentation.ui.training.moved
import com.ricardofrango.squadify.presentation.ui.training.phasesOfMonth
import com.ricardofrango.squadify.presentation.ui.training.rangeError
import com.ricardofrango.squadify.presentation.ui.training.toRequest
import com.ricardofrango.squadify.presentation.ui.training.withNewPhase
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The plan editors (design 40): what can be saved, dates against the season, and copying.
class PlanFormTest {
    private val season = Season("s", "c", "26/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), true)

    @Test
    fun datesAreCheckedAgainstTheSeason() {
        assertEquals(PlanDateError.AFTER_SEASON, rangeError(LocalDate(2027, 1, 5), LocalDate(2027, 7, 15), season))
        assertEquals(PlanDateError.BEFORE_SEASON, rangeError(LocalDate(2026, 8, 20), LocalDate(2026, 9, 10), season))
        assertEquals(PlanDateError.END_BEFORE_START, rangeError(LocalDate(2026, 12, 1), LocalDate(2026, 11, 1), season))
        assertNull(rangeError(LocalDate(2026, 9, 28), LocalDate(2026, 12, 20), season))
    }

    @Test
    fun monthsAndWeeksNeedAFocusButTheSeasonDoesNot() {
        assertFalse(PlanForm().isValid(TrainingPlanLevel.MONTH, season))
        assertTrue(PlanForm(focus = "Pressing").isValid(TrainingPlanLevel.WEEK, season))
        assertTrue(PlanForm().isValid(TrainingPlanLevel.SEASON, season))
    }

    @Test
    fun aPhasePastTheSeasonOrWithoutANameBlocksSaving() {
        val late = PlanForm(phases = listOf(TrainingPhase("2nd half", LocalDate(2027, 1, 5), LocalDate(2027, 7, 15))))
        assertFalse(late.isValid(TrainingPlanLevel.SEASON, season))
        val unnamed = PlanForm(phases = listOf(TrainingPhase(" ", LocalDate(2027, 1, 5), LocalDate(2027, 4, 25))))
        assertFalse(unnamed.isValid(TrainingPlanLevel.SEASON, season))
    }

    @Test
    fun aNewPhaseStartsTheDayAfterTheLastOne() {
        val form = PlanForm(phases = listOf(TrainingPhase("Pre-season", LocalDate(2026, 9, 1), LocalDate(2026, 9, 27)))).withNewPhase(season)
        assertEquals(LocalDate(2026, 9, 28), form.phases.last().startDate)
        assertEquals(season.endDate, form.phases.last().endDate)
        assertEquals(season.startDate, PlanForm().withNewPhase(season).phases.single().startDate)
    }

    @Test
    fun theRequestDropsBlankObjectivesAndWhatTheLevelDoesNotHave() {
        val form = PlanForm(focus = " Pressing ", objectives = listOf("Press on the back pass", " "), intensity = TrainingIntensity.HIGH)
        val month = form.toRequest(TrainingPlanLevel.MONTH, LocalDate(2026, 11, 1))
        assertEquals("Pressing", month.focus)
        assertEquals(listOf("Press on the back pass"), month.objectives)
        assertNull(month.intensity)
        assertEquals(TrainingIntensity.HIGH, form.toRequest(TrainingPlanLevel.WEEK, LocalDate(2026, 10, 12)).intensity)
    }

    @Test
    fun copyingTakesThePreviousFocusObjectivesAndNotes() {
        val previous = TrainingPlan("p", "ag", TrainingPlanLevel.WEEK, LocalDate(2026, 10, 12), focus = "Playing out", objectives = listOf("GK as the extra man"), intensity = TrainingIntensity.MEDIUM)
        assertFalse(PlanForm().hasTypedContent())
        val copied = PlanForm().copiedFrom(previous)
        assertEquals("Playing out", copied.focus)
        assertEquals(listOf("GK as the extra man"), copied.objectives)
        assertEquals(TrainingIntensity.MEDIUM, copied.intensity)
        assertTrue(PlanForm(focus = "Third-man runs").hasTypedContent())
    }

    @Test
    fun reorderingMovesOneItem() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved(0, 1))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").moved(0, 5))
    }

    @Test
    fun aMonthShowsThePhasesThatOverlapIt() {
        val phases = listOf(
            TrainingPhase("1st half", LocalDate(2026, 9, 28), LocalDate(2026, 12, 20)),
            TrainingPhase("Break", LocalDate(2026, 12, 21), LocalDate(2027, 1, 4))
        )
        assertEquals(listOf("1st half", "Break"), phasesOfMonth(phases, LocalDate(2026, 12, 1), season).map { it.name })
        assertEquals(listOf("1st half"), phasesOfMonth(phases, LocalDate(2026, 10, 1), season).map { it.name })
    }
}
