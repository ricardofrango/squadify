package com.ricardofrango.squadify.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.presentation.ui.addplayerstoagegroup.AddPlayersToAgeGroupScreen
import com.ricardofrango.squadify.presentation.ui.agegroupdetails.AgeGroupDetailsScreen
import com.ricardofrango.squadify.presentation.ui.createagegroups.CreateAgeGroupsScreen
import com.ricardofrango.squadify.presentation.ui.creatematch.CreateMatchScreen
import com.ricardofrango.squadify.presentation.ui.createplayers.CreatePlayersScreen
import com.ricardofrango.squadify.presentation.ui.createteams.CreateTeamsScreen
import com.ricardofrango.squadify.presentation.ui.editagegroup.EditAgeGroupScreen
import com.ricardofrango.squadify.presentation.ui.editplayer.EditPlayerScreen
import com.ricardofrango.squadify.presentation.ui.editteam.EditTeamScreen
import com.ricardofrango.squadify.presentation.ui.fullstats.FullStatsScreen
import com.ricardofrango.squadify.presentation.ui.livematch.LiveMatchScreen
import com.ricardofrango.squadify.presentation.ui.matchdetails.MatchDetailsScreen
import com.ricardofrango.squadify.presentation.ui.matchrecord.MatchRecordScreen
import com.ricardofrango.squadify.presentation.ui.matchreport.MatchReportScreen
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentDetailsScreen
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentEditScreen
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentFormScreen
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentsScreen
import com.ricardofrango.squadify.presentation.ui.playerdetails.PlayerDetailsScreen
import com.ricardofrango.squadify.presentation.ui.teamdetails.TeamDetailsScreen
import com.ricardofrango.squadify.presentation.ui.training.CreateSessionsScreen
import com.ricardofrango.squadify.presentation.ui.training.ExerciseLibraryScreen
import com.ricardofrango.squadify.presentation.ui.training.MonthPlanScreen
import com.ricardofrango.squadify.presentation.ui.training.PeriodPlanEditScreen
import com.ricardofrango.squadify.presentation.ui.training.RatePlayersScreen
import com.ricardofrango.squadify.presentation.ui.training.SeasonPlanEditScreen
import com.ricardofrango.squadify.presentation.ui.training.SeasonPlanScreen
import com.ricardofrango.squadify.presentation.ui.training.SessionDetailsScreen
import com.ricardofrango.squadify.presentation.ui.training.SessionEditScreen
import com.ricardofrango.squadify.presentation.ui.training.WeekPlanScreen
import kotlinx.datetime.LocalDate

// The screens both apps have (TECHNICAL_DECISIONS §72): the age group, team, player, opponent, match,
// live game, record, report and stats screens, and training, which only the clubs app opens (the coach
// app shows no way into it). Each app's navigation registers them with `sharedScreens(navigator)` and
// adds its own. What a screen leaves out in an app is decided by that app's AppFeatures, in the screen.

// Key used on a back-stack entry's SavedStateHandle to signal "a season, age group, team, player, or
// match was just created/changed, force a reload" -- the standard Compose Navigation pattern for a
// screen to return a result to the screen that pushed it. See ClubDetailsScreen's `refreshClubDetails`
// doc.
const val KEY_CLUB_STRUCTURE_CHANGED = "clubStructureChanged"

// Opponents (SELECT) -> Schedule match: the id of the opponent just picked.
private const val KEY_OPPONENT_PICKED = "opponentPicked"

// Create opponent (SELECT) -> Schedule match: the new opponent's name, for "… created and selected".
private const val KEY_OPPONENT_CREATED = "opponentCreated"

// Create sessions -> Age Group Details: how many were created, for "24 sessions created".
private const val KEY_TRAINING_MESSAGE = "trainingMessage"

/**
 * Sets [key] on [entry]'s results only while it's still in the back stack. An entry captured earlier
 * can be gone by the time it's used: after the app is recreated (a configuration change, e.g. unlocking
 * the phone mid-match, or a process restart) the back stack is restored as new entries, and the old
 * one is no longer anyone's. Skipping the flag then is safe: nothing is returning to that screen at
 * that moment, and the restored screen on top registers its onDispose again against the restored
 * entry, so the flag is still set when the user really leaves.
 */
fun AppNavigator.setIfAlive(entry: AppBackStackEntry?, key: String, value: Any?) {
    if (entry != null && isAlive(entry)) entry.savedStateHandle[key] = value
}

/**
 * Consume the "structure changed" result on [entry] and cascade it one level further down the back
 * stack. A Create* screen only ever sets the flag on its immediate `previousBackStackEntry`, so when
 * the create action is taken two or more screens deep (e.g. creating a team from AgeGroupDetails,
 * which itself sits on top of ClubDetails) the screens underneath the launcher would otherwise never
 * learn they're stale. Each intermediate detail screen forwards the signal on as it consumes it, so
 * the reload reaches every screen below when the user pops back.
 */
fun AppNavigator.consumeAndCascadeStructureChanged(entry: AppBackStackEntry) {
    entry.savedStateHandle[KEY_CLUB_STRUCTURE_CHANGED] = false
    previousBackStackEntry?.savedStateHandle?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
}

/** Registers every shared screen's content, and where each one navigates to. */
fun ScreenRegistry.sharedScreens(navigator: AppNavigator) {
    screen<Screen.EditAgeGroup> { backStackEntry ->
        val screen: Screen.EditAgeGroup = backStackEntry.toRoute()
        EditAgeGroupScreen(
            ageGroupId = screen.ageGroupId,
            onNavigateBack = { navigator.popBackStack() },
            onAgeGroupUpdated = {
                // Age Group Details (and the club below it) show the name and years: reload on return.
                navigator.previousBackStackEntry?.savedStateHandle?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.EditTeam> { backStackEntry ->
        val screen: Screen.EditTeam = backStackEntry.toRoute()
        EditTeamScreen(
            teamId = screen.teamId,
            onNavigateBack = { navigator.popBackStack() },
            onTeamUpdated = {
                // Team Details (and the age group / club below it) show the name: reload on return.
                navigator.previousBackStackEntry?.savedStateHandle?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            },
            onTeamDeleted = {
                // Team Details is gone with the team: back to what opened it (the age group), which
                // reloads and passes the refresh down (its teams, matches and the club's counts changed).
                if (!navigator.popBackStack<Screen.TeamDetails>(inclusive = true)) navigator.popBackStack()
                navigator.currentBackStackEntry?.savedStateHandle?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
            }
        )
    }

    screen<Screen.CreateAgeGroups> { backStackEntry ->
        val screen: Screen.CreateAgeGroups = backStackEntry.toRoute()
        CreateAgeGroupsScreen(
            clubId = screen.clubId,
            clubName = screen.clubName,
            sport = SportType.valueOf(screen.sport),
            initialSeasonId = screen.initialSeasonId,
            onNavigateBack = {
                navigator.popBackStack()
            },
            onAgeGroupsCreated = {
                navigator.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.CreateTeams> { backStackEntry ->
        val screen: Screen.CreateTeams = backStackEntry.toRoute()
        CreateTeamsScreen(
            clubId = screen.clubId,
            clubName = screen.clubName,
            sport = SportType.valueOf(screen.sport),
            initialAgeGroupId = screen.initialAgeGroupId,
            onNavigateBack = {
                navigator.popBackStack()
            },
            onTeamsCreated = {
                navigator.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.AddPlayersToAgeGroup> { backStackEntry ->
        val screen: Screen.AddPlayersToAgeGroup = backStackEntry.toRoute()
        // Set by CreatePlayers when the "New player" shortcut is used -- reload the eligible list
        // so the just-registered players show up, and pass the signal on down (ClubDetails /
        // AgeGroupDetails) so their player lists refresh too.
        val structureChanged by backStackEntry.savedStateHandle
            .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
            .collectAsStateWithLifecycle()
        if (structureChanged) {
            navigator.consumeAndCascadeStructureChanged(backStackEntry)
        }
        AddPlayersToAgeGroupScreen(
            clubId = screen.clubId,
            clubName = screen.clubName,
            initialAgeGroupId = screen.initialAgeGroupId,
            onNavigateBack = {
                navigator.popBackStack()
            },
            onNavigateToCreatePlayers = { ageGroupId ->
                navigator.navigate(Screen.CreatePlayers(screen.clubId, screen.clubName, ageGroupId))
            },
            onPlayersAdded = {
                navigator.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            },
            refreshOnReturn = structureChanged
        )
    }

    screen<Screen.AgeGroupDetails> { backStackEntry ->
        val screen: Screen.AgeGroupDetails = backStackEntry.toRoute()
        val structureChanged by backStackEntry.savedStateHandle
            .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
            .collectAsStateWithLifecycle()
        val sessionsCreated by backStackEntry.savedStateHandle
            .getStateFlow<Int?>(KEY_TRAINING_MESSAGE, null)
            .collectAsStateWithLifecycle()
        if (structureChanged) {
            navigator.consumeAndCascadeStructureChanged(backStackEntry)
        }
        AgeGroupDetailsScreen(
            ageGroupId = screen.ageGroupId,
            onNavigateBack = { navigator.popBackStack() },
            onNavigateToTeam = { teamId -> navigator.navigate(Screen.TeamDetails(teamId)) },
            onNavigateToFullStats = { ageGroupId -> navigator.navigate(Screen.FullStats(ageGroupId = ageGroupId)) },
            onNavigateToPlayer = { playerId -> navigator.navigate(Screen.PlayerDetails(playerId)) },
            onNavigateToMember = { clubId, userId -> navigator.navigate(Screen.ClubMemberDetails(clubId, userId = userId)) },
            onNavigateToEditAgeGroup = { ageGroupId -> navigator.navigate(Screen.EditAgeGroup(ageGroupId)) },
            onNavigateToMatch = { matchId -> navigator.navigate(Screen.MatchDetails(matchId)) },
            onNavigateToAddPlayers = { clubId, clubName, ageGroupId ->
                navigator.navigate(Screen.AddPlayersToAgeGroup(clubId, clubName, ageGroupId))
            },
            onNavigateToAddStaff = { clubId, clubName, ageGroupId ->
                navigator.navigate(Screen.AddStaffToAgeGroup(clubId, clubName, ageGroupId))
            },
            // The coach app's owner; its screen is the coach app's own.
            onNavigateToInviteStaff = { ageGroupId, ageGroupName, spaceName ->
                navigator.navigate(Screen.InviteStaff(ageGroupId, ageGroupName, spaceName))
            },
            onNavigateToCreateTeams = { clubId, clubName, sport ->
                navigator.navigate(Screen.CreateTeams(clubId, clubName, sport.name))
            },
            onNavigateToCreateMatch = { ageGroupId ->
                navigator.navigate(Screen.CreateMatch(ageGroupId = ageGroupId))
            },
            onNavigateToSeasonPlan = { ageGroupId, edit ->
                navigator.navigate(if (edit) Screen.EditSeasonPlan(ageGroupId) else Screen.SeasonPlan(ageGroupId))
            },
            onNavigateToMonthPlan = { ageGroupId, month, edit ->
                navigator.navigate(if (edit) Screen.EditMonthPlan(ageGroupId, month.toString()) else Screen.MonthPlan(ageGroupId, month.toString()))
            },
            onNavigateToCreateSessions = { ageGroupId -> navigator.navigate(Screen.CreateTrainingSessions(ageGroupId)) },
            onNavigateToAddSession = { ageGroupId, date -> navigator.navigate(Screen.EditTrainingSession(ageGroupId, date = date?.toString())) },
            onNavigateToSession = { sessionId -> navigator.navigate(Screen.TrainingSession(sessionId)) },
            sessionsCreated = sessionsCreated,
            onMessageShown = { backStackEntry.savedStateHandle[KEY_TRAINING_MESSAGE] = null },
            onNavigateToWeekPlan = { ageGroupId, monday, edit ->
                navigator.navigate(if (edit) Screen.EditWeekPlan(ageGroupId, monday.toString()) else Screen.WeekPlan(ageGroupId, monday.toString()))
            },
            refreshOnReturn = structureChanged
        )
    }

    screen<Screen.CreatePlayers> { backStackEntry ->
        val screen: Screen.CreatePlayers = backStackEntry.toRoute()
        CreatePlayersScreen(
            clubId = screen.clubId,
            clubName = screen.clubName,
            ageGroupId = screen.ageGroupId,
            onNavigateBack = {
                navigator.popBackStack()
            },
            onPlayersCreated = {
                navigator.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.TeamDetails> { backStackEntry ->
        val screen: Screen.TeamDetails = backStackEntry.toRoute()
        val structureChanged by backStackEntry.savedStateHandle
            .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
            .collectAsStateWithLifecycle()
        if (structureChanged) {
            navigator.consumeAndCascadeStructureChanged(backStackEntry)
        }
        TeamDetailsScreen(
            teamId = screen.teamId,
            onNavigateToMember = { clubId, userId -> navigator.navigate(Screen.ClubMemberDetails(clubId, userId = userId)) },
            onNavigateBack = {
                navigator.popBackStack()
            },
            onNavigateToAgeGroup = { ageGroupId ->
                navigator.navigate(Screen.AgeGroupDetails(ageGroupId))
            },
            onNavigateToPlayer = { playerId, seasonId, filter ->
                navigator.navigate(Screen.PlayerDetails(playerId, seasonId = seasonId, statsFilter = filter.name))
            },
            onNavigateToMatch = { matchId ->
                navigator.navigate(Screen.MatchDetails(matchId))
            },
            onScheduleMatch = {
                navigator.navigate(Screen.CreateMatch(teamId = screen.teamId))
            },
            onOpenFullStats = { navigator.navigate(Screen.FullStats(teamId = screen.teamId)) },
            // The team-editing screen doesn't exist yet -- this affordance stays inert.
            onEditTeam = { navigator.navigate(Screen.EditTeam(screen.teamId)) },
            refreshOnReturn = structureChanged
        )
    }

    screen<Screen.FullStats> { backStackEntry ->
        val screen: Screen.FullStats = backStackEntry.toRoute()
        FullStatsScreen(
            teamId = screen.teamId,
            ageGroupId = screen.ageGroupId,
            onNavigateBack = { navigator.popBackStack() },
            onOpenPlayer = { playerId, seasonId -> navigator.navigate(Screen.PlayerDetails(playerId, seasonId = seasonId)) }
        )
    }

    screen<Screen.CreateMatch> { backStackEntry ->
        val screen: Screen.CreateMatch = backStackEntry.toRoute()
        val pickedOpponentId by backStackEntry.savedStateHandle
            .getStateFlow<String?>(KEY_OPPONENT_PICKED, null)
            .collectAsStateWithLifecycle()
        val createdOpponentName by backStackEntry.savedStateHandle
            .getStateFlow<String?>(KEY_OPPONENT_CREATED, null)
            .collectAsStateWithLifecycle()
        CreateMatchScreen(
            ageGroupId = screen.ageGroupId,
            teamId = screen.teamId,
            onNavigateBack = { navigator.popBackStack() },
            onChooseOpponent = { clubId, ageGroupId, selectedOpponentId ->
                navigator.navigate(Screen.Opponents(clubId, select = true, ageGroupId = ageGroupId, selectedOpponentId = selectedOpponentId))
            },
            pickedOpponentId = pickedOpponentId,
            onPickedOpponentConsumed = { backStackEntry.savedStateHandle[KEY_OPPONENT_PICKED] = null },
            createdOpponentName = createdOpponentName,
            onCreatedOpponentShown = { backStackEntry.savedStateHandle[KEY_OPPONENT_CREATED] = null },
            onMatchCreated = {
                navigator.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.Opponents> { backStackEntry ->
        val screen: Screen.Opponents = backStackEntry.toRoute()
        // Set by Create opponent (BROWSE), so the list reloads on return.
        val opponentsChanged by backStackEntry.savedStateHandle
            .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
            .collectAsStateWithLifecycle()
        if (opponentsChanged) {
            backStackEntry.savedStateHandle[KEY_CLUB_STRUCTURE_CHANGED] = false
        }
        OpponentsScreen(
            clubId = screen.clubId,
            ageGroupId = screen.ageGroupId,
            selectMode = screen.select,
            selectedOpponentId = screen.selectedOpponentId,
            onNavigateBack = { navigator.popBackStack() },
            onOpponentClick = { opponent ->
                if (screen.select) {
                    navigator.setIfAlive(navigator.previousBackStackEntry, KEY_OPPONENT_PICKED, opponent.id)
                    navigator.popBackStack()
                } else {
                    navigator.navigate(Screen.OpponentDetails(opponent.id))
                }
            },
            onCreateOpponent = { name ->
                navigator.navigate(Screen.CreateOpponent(screen.clubId, screen.ageGroupId, name, select = screen.select))
            },
            refresh = opponentsChanged
        )
    }

    screen<Screen.OpponentDetails> { backStackEntry ->
        val screen: Screen.OpponentDetails = backStackEntry.toRoute()
        // Set by Edit opponent; passed on so the list underneath reloads too.
        val edited by backStackEntry.savedStateHandle
            .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
            .collectAsStateWithLifecycle()
        if (edited) {
            navigator.consumeAndCascadeStructureChanged(backStackEntry)
        }
        OpponentDetailsScreen(
            opponentId = screen.opponentId,
            onNavigateBack = { navigator.popBackStack() },
            onOpenMatch = { matchId -> navigator.navigate(Screen.MatchDetails(matchId)) },
            onEdit = { navigator.navigate(Screen.EditOpponent(screen.opponentId)) },
            refresh = edited
        )
    }

    screen<Screen.EditOpponent> { backStackEntry ->
        val screen: Screen.EditOpponent = backStackEntry.toRoute()
        OpponentEditScreen(
            opponentId = screen.opponentId,
            onNavigateBack = { navigator.popBackStack() },
            onSaved = {
                navigator.setIfAlive(navigator.previousBackStackEntry, KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.CreateOpponent> { backStackEntry ->
        val screen: Screen.CreateOpponent = backStackEntry.toRoute()
        OpponentFormScreen(
            clubId = screen.clubId,
            ageGroupId = screen.ageGroupId,
            initialName = screen.name,
            onNavigateBack = { navigator.popBackStack() },
            onCreated = { opponent ->
                if (screen.select) {
                    // Picking for a match: skip the list and go straight back to the form, with the
                    // new opponent selected (design 38d).
                    val matchEntry = runCatching { navigator.getBackStackEntry<Screen.CreateMatch>() }.getOrNull()
                    navigator.setIfAlive(matchEntry, KEY_OPPONENT_PICKED, opponent.id)
                    navigator.setIfAlive(matchEntry, KEY_OPPONENT_CREATED, opponent.name)
                    if (!navigator.popBackStack<Screen.CreateMatch>(inclusive = false)) navigator.popBackStack()
                } else {
                    navigator.setIfAlive(navigator.previousBackStackEntry, KEY_CLUB_STRUCTURE_CHANGED, true)
                    navigator.popBackStack()
                }
            }
        )
    }

    screen<Screen.SeasonPlan> { backStackEntry ->
        val screen: Screen.SeasonPlan = backStackEntry.toRoute()
        // Set by the plan editors, so the plan reloads on return.
        val planChanged by backStackEntry.savedStateHandle
            .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
            .collectAsStateWithLifecycle()
        if (planChanged) navigator.consumeAndCascadeStructureChanged(backStackEntry)
        SeasonPlanScreen(
            ageGroupId = screen.ageGroupId,
            onNavigateBack = { navigator.popBackStack() },
            onEdit = { navigator.navigate(Screen.EditSeasonPlan(screen.ageGroupId)) },
            onOpenMonth = { month -> navigator.navigate(Screen.MonthPlan(screen.ageGroupId, month.toString())) },
            refresh = planChanged
        )
    }

    screen<Screen.EditSeasonPlan> { backStackEntry ->
        val screen: Screen.EditSeasonPlan = backStackEntry.toRoute()
        SeasonPlanEditScreen(
            ageGroupId = screen.ageGroupId,
            onNavigateBack = { navigator.popBackStack() },
            onSaved = {
                navigator.setIfAlive(navigator.previousBackStackEntry, KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.MonthPlan> { backStackEntry ->
        val screen: Screen.MonthPlan = backStackEntry.toRoute()
        val planChanged by backStackEntry.savedStateHandle
            .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
            .collectAsStateWithLifecycle()
        if (planChanged) navigator.consumeAndCascadeStructureChanged(backStackEntry)
        MonthPlanScreen(
            ageGroupId = screen.ageGroupId,
            month = LocalDate.parse(screen.month),
            onNavigateBack = { navigator.popBackStack() },
            onEdit = { navigator.navigate(Screen.EditMonthPlan(screen.ageGroupId, screen.month)) },
            onOpenWeek = { monday -> navigator.navigate(Screen.WeekPlan(screen.ageGroupId, monday.toString())) },
            refresh = planChanged
        )
    }

    screen<Screen.EditMonthPlan> { backStackEntry ->
        val screen: Screen.EditMonthPlan = backStackEntry.toRoute()
        PeriodPlanEditScreen(
            ageGroupId = screen.ageGroupId,
            level = TrainingPlanLevel.MONTH,
            periodStart = LocalDate.parse(screen.month),
            onNavigateBack = { navigator.popBackStack() },
            onSaved = {
                navigator.setIfAlive(navigator.previousBackStackEntry, KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.WeekPlan> { backStackEntry ->
        val screen: Screen.WeekPlan = backStackEntry.toRoute()
        val planChanged by backStackEntry.savedStateHandle
            .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
            .collectAsStateWithLifecycle()
        if (planChanged) navigator.consumeAndCascadeStructureChanged(backStackEntry)
        WeekPlanScreen(
            ageGroupId = screen.ageGroupId,
            monday = LocalDate.parse(screen.monday),
            onNavigateBack = { navigator.popBackStack() },
            onEdit = { navigator.navigate(Screen.EditWeekPlan(screen.ageGroupId, screen.monday)) },
            onOpenSession = { sessionId -> navigator.navigate(Screen.TrainingSession(sessionId)) },
            onAddSession = { date -> navigator.navigate(Screen.EditTrainingSession(screen.ageGroupId, date = date.toString())) },
            refresh = planChanged
        )
    }

    screen<Screen.EditWeekPlan> { backStackEntry ->
        val screen: Screen.EditWeekPlan = backStackEntry.toRoute()
        PeriodPlanEditScreen(
            ageGroupId = screen.ageGroupId,
            level = TrainingPlanLevel.WEEK,
            periodStart = LocalDate.parse(screen.monday),
            onNavigateBack = { navigator.popBackStack() },
            onSaved = {
                navigator.setIfAlive(navigator.previousBackStackEntry, KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.CreateTrainingSessions> { backStackEntry ->
        val screen: Screen.CreateTrainingSessions = backStackEntry.toRoute()
        CreateSessionsScreen(
            ageGroupId = screen.ageGroupId,
            onNavigateBack = { navigator.popBackStack() },
            onCreated = { count ->
                navigator.previousBackStackEntry?.let { previous ->
                    navigator.setIfAlive(previous, KEY_CLUB_STRUCTURE_CHANGED, true)
                    navigator.setIfAlive(previous, KEY_TRAINING_MESSAGE, count)
                }
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.TrainingSession> { backStackEntry ->
        val screen: Screen.TrainingSession = backStackEntry.toRoute()
        val sessionChanged by backStackEntry.savedStateHandle
            .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
            .collectAsStateWithLifecycle()
        if (sessionChanged) navigator.consumeAndCascadeStructureChanged(backStackEntry)
        SessionDetailsScreen(
            sessionId = screen.sessionId,
            onNavigateBack = { navigator.popBackStack() },
            onEdit = { ageGroupId -> navigator.navigate(Screen.EditTrainingSession(ageGroupId, sessionId = screen.sessionId)) },
            onOpenWeekPlan = { ageGroupId, monday, edit ->
                navigator.navigate(if (edit) Screen.EditWeekPlan(ageGroupId, monday.toString()) else Screen.WeekPlan(ageGroupId, monday.toString()))
            },
            onAddExercises = { navigator.navigate(Screen.ExerciseLibrary(screen.sessionId)) },
            onRatePlayers = { navigator.navigate(Screen.RateTraining(screen.sessionId)) },
            onChanged = { deleted ->
                navigator.setIfAlive(navigator.previousBackStackEntry, KEY_CLUB_STRUCTURE_CHANGED, true)
                if (deleted) navigator.popBackStack()
            },
            refresh = sessionChanged
        )
    }

    screen<Screen.RateTraining> { backStackEntry ->
        val screen: Screen.RateTraining = backStackEntry.toRoute()
        RatePlayersScreen(
            sessionId = screen.sessionId,
            onNavigateBack = { navigator.popBackStack() },
            onSaved = {
                navigator.setIfAlive(navigator.previousBackStackEntry, KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.ExerciseLibrary> { backStackEntry ->
        val screen: Screen.ExerciseLibrary = backStackEntry.toRoute()
        ExerciseLibraryScreen(
            sessionId = screen.sessionId,
            onNavigateBack = { navigator.popBackStack() },
            onAdded = {
                navigator.setIfAlive(navigator.previousBackStackEntry, KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.EditTrainingSession> { backStackEntry ->
        val screen: Screen.EditTrainingSession = backStackEntry.toRoute()
        SessionEditScreen(
            ageGroupId = screen.ageGroupId,
            sessionId = screen.sessionId,
            date = screen.date?.let { LocalDate.parse(it) },
            onNavigateBack = { navigator.popBackStack() },
            onSaved = {
                navigator.setIfAlive(navigator.previousBackStackEntry, KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.MatchDetails> { backStackEntry ->
        val screen: Screen.MatchDetails = backStackEntry.toRoute()
        val structureChanged by backStackEntry.savedStateHandle
            .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
            .collectAsStateWithLifecycle()
        if (structureChanged) {
            navigator.consumeAndCascadeStructureChanged(backStackEntry)
        }
        MatchDetailsScreen(
            matchId = screen.matchId,
            onNavigateBack = { navigator.popBackStack() },
            onEnterLiveMatch = { matchId -> navigator.navigate(Screen.LiveMatch(matchId)) },
            onOpenReport = { matchId, edit -> navigator.navigate(Screen.MatchReport(matchId, edit)) },
            onOpenRecord = { matchId -> navigator.navigate(Screen.MatchRecord(matchId)) },
            onOpenPlayer = { playerId, seasonId -> navigator.navigate(Screen.PlayerDetails(playerId, seasonId = seasonId)) },
            onRedirectToLiveMatch = { matchId ->
                // Replace this entry rather than push on top of it -- otherwise back from
                // LiveMatch lands back on this screen, which immediately redirects again.
                navigator.navigate(Screen.LiveMatch(matchId)) {
                    popUpTo(screen) { inclusive = true }
                }
            },
            refreshOnReturn = structureChanged
        )
    }

    screen<Screen.LiveMatch> { backStackEntry ->
        val screen: Screen.LiveMatch = backStackEntry.toRoute()
        // Captured once, while this entry is on top -- previousBackStackEntry at this point is
        // MatchDetails' entry. Flagging it in onDispose (rather than only from onNavigateBack)
        // covers every way this screen can be left, including the system/gesture back button,
        // which pops the stack directly without ever calling onNavigateBack.
        val matchDetailsEntry = navigator.previousBackStackEntry
        DisposableEffect(Unit) {
            onDispose {
                // Every action here (kick off, half/full time, events) mutates the match, and
                // there's no separate "nothing changed" exit -- always flag it so MatchDetails
                // (and, cascading further, the screen that opened it) reload instead of showing a
                // stale status/score, e.g. the "enter live match" button after full time.
                // On an activity relaunch the entry is already destroyed here -- see setIfAlive.
                navigator.setIfAlive(matchDetailsEntry, KEY_CLUB_STRUCTURE_CHANGED, true)
            }
        }
        LiveMatchScreen(
            matchId = screen.matchId,
            onNavigateBack = { navigator.popBackStack() },
            onOpenReport = { matchId, edit -> navigator.navigate(Screen.MatchReport(matchId, edit)) }
        )
    }

    screen<Screen.MatchReport> { backStackEntry ->
        val screen: Screen.MatchReport = backStackEntry.toRoute()
        // Captured while this entry is on top: the screen that opened the report (Match Details).
        // Flagged as soon as a save succeeds rather than only from onNavigateBack, because the
        // system/gesture back button pops this entry directly without calling it -- the same reason
        // LiveMatch flags its caller in onDispose. Match Details then reloads and cascades to Home.
        val openerEntry = remember { navigator.previousBackStackEntry }
        MatchReportScreen(
            matchId = screen.matchId,
            startEditing = screen.edit,
            onNavigateBack = { navigator.popBackStack() },
            onReportSaved = { navigator.setIfAlive(openerEntry, KEY_CLUB_STRUCTURE_CHANGED, true) }
        )
    }

    screen<Screen.MatchRecord> { backStackEntry ->
        val screen: Screen.MatchRecord = backStackEntry.toRoute()
        // Saved: back to Match Details, which reloads (and cascades to the lists that show the score).
        MatchRecordScreen(
            matchId = screen.matchId,
            onNavigateBack = { navigator.popBackStack() },
            onSaved = {
                navigator.setIfAlive(navigator.previousBackStackEntry, KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            }
        )
    }

    screen<Screen.PlayerDetails> { backStackEntry ->
        val screen: Screen.PlayerDetails = backStackEntry.toRoute()
        val structureChanged by backStackEntry.savedStateHandle
            .getStateFlow(KEY_CLUB_STRUCTURE_CHANGED, false)
            .collectAsStateWithLifecycle()
        if (structureChanged) {
            navigator.consumeAndCascadeStructureChanged(backStackEntry)
        }
        PlayerDetailsScreen(
            playerId = screen.playerId,
            onNavigateBack = {
                navigator.popBackStack()
            },
            onEditPlayer = { playerId ->
                navigator.navigate(Screen.EditPlayer(playerId))
            },
            onOpenMatch = { matchId ->
                navigator.navigate(Screen.MatchDetails(matchId))
            },
            initialSeasonId = screen.seasonId,
            initialFilter = screen.statsFilter?.let { name -> StatsFilter.entries.firstOrNull { it.name == name } },
            refreshOnReturn = structureChanged
        )
    }

    screen<Screen.EditPlayer> { backStackEntry ->
        val screen: Screen.EditPlayer = backStackEntry.toRoute()
        EditPlayerScreen(
            playerId = screen.playerId,
            onNavigateBack = { navigator.popBackStack() },
            onPlayerUpdated = {
                navigator.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
                navigator.popBackStack()
            },
            // Saved straight away: the screen below reloads when it's back on top.
            onPhotoChanged = {
                navigator.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(KEY_CLUB_STRUCTURE_CHANGED, true)
            }
        )
    }
}
