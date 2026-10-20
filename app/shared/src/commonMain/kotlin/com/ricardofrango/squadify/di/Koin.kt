package com.ricardofrango.squadify.di

import com.ricardofrango.squadify.data.local.SessionRepositoryImpl
import com.ricardofrango.squadify.data.local.UiPreferencesRepositoryImpl
import com.ricardofrango.squadify.data.remote.*
import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.repository.*
import com.ricardofrango.squadify.domain.service.NoOpPushTokenProvider
import com.ricardofrango.squadify.domain.service.PushTokenProvider
import com.ricardofrango.squadify.domain.service.SessionEventBus
import com.ricardofrango.squadify.domain.usecase.*
import com.ricardofrango.squadify.presentation.ui.account.AccountViewModel
import com.ricardofrango.squadify.presentation.ui.clubdetails.ClubDetailsViewModel
import com.ricardofrango.squadify.presentation.ui.clubdetails.ClubPlayersViewModel
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentDetailsViewModel
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentFormViewModel
import com.ricardofrango.squadify.presentation.ui.opponents.OpponentsViewModel
import com.ricardofrango.squadify.presentation.ui.training.CreateSessionsViewModel
import com.ricardofrango.squadify.presentation.ui.training.SessionDetailsViewModel
import com.ricardofrango.squadify.presentation.ui.training.SessionEditViewModel
import com.ricardofrango.squadify.presentation.ui.training.ExerciseLibraryViewModel
import com.ricardofrango.squadify.presentation.ui.training.RatePlayersViewModel
import com.ricardofrango.squadify.presentation.ui.training.PlanEditViewModel
import com.ricardofrango.squadify.presentation.ui.training.TrainingPlansViewModel
import com.ricardofrango.squadify.presentation.ui.editclub.EditClubViewModel
import com.ricardofrango.squadify.presentation.ui.clubmember.ClubMemberDetailsViewModel
import com.ricardofrango.squadify.presentation.ui.createseason.CreateSeasonViewModel
import com.ricardofrango.squadify.presentation.ui.createagegroups.CreateAgeGroupsViewModel
import com.ricardofrango.squadify.presentation.ui.createteams.CreateTeamsViewModel
import com.ricardofrango.squadify.presentation.ui.editteam.EditTeamViewModel
import com.ricardofrango.squadify.presentation.ui.editagegroup.EditAgeGroupViewModel
import com.ricardofrango.squadify.presentation.ui.addplayerstoagegroup.AddPlayersToAgeGroupViewModel
import com.ricardofrango.squadify.presentation.ui.createplayers.CreatePlayersViewModel
import com.ricardofrango.squadify.presentation.ui.agegroupdetails.AgeGroupDetailsViewModel
import com.ricardofrango.squadify.presentation.ui.teamdetails.TeamDetailsViewModel
import com.ricardofrango.squadify.presentation.ui.creatematch.CreateMatchViewModel
import com.ricardofrango.squadify.presentation.ui.matchdetails.MatchDetailsViewModel
import com.ricardofrango.squadify.presentation.ui.matchreport.MatchReportViewModel
import com.ricardofrango.squadify.presentation.ui.livematch.LiveMatchViewModel
import com.ricardofrango.squadify.presentation.ui.addstafftoagegroup.AddStaffToAgeGroupViewModel
import com.ricardofrango.squadify.presentation.ui.createstaff.CreateStaffViewModel
import com.ricardofrango.squadify.presentation.ui.playerdetails.PlayerDetailsViewModel
import com.ricardofrango.squadify.presentation.ui.editplayer.EditPlayerViewModel
import com.ricardofrango.squadify.presentation.ui.dashboard.DashboardViewModel
import com.ricardofrango.squadify.presentation.ui.login.LoginViewModel
import com.ricardofrango.squadify.presentation.ui.forgotpassword.ForgotPasswordViewModel
import com.ricardofrango.squadify.presentation.ui.changeemail.ChangeEmailViewModel
import com.ricardofrango.squadify.presentation.ui.register.RegisterViewModel
import com.ricardofrango.squadify.presentation.ui.splash.SplashViewModel
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val publicHttpClientQualifier = named("publicHttpClient")

val dataModule = module {
    // No auth of any kind -- used only by AuthRepositoryImpl (login/register/the raw refresh call).
    // Keeping this entirely separate from the authenticated client below means a bad-login 401 (same
    // AUTH_001 code the server uses for an expired/invalid JWT -- see Application.kt) can never be
    // mistaken for a dead session and force-logout the user while they're still on the Login screen.
    single(publicHttpClientQualifier) {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        }
        HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        println("HTTP Client: $message")
                    }
                }
                level = LogLevel.ALL
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
            // Live match updates (LiveMatchUpdate) -- see MatchRepositoryImpl.observeLiveMatch.
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(json)
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }
    }

    // Every other repository's client -- see createAuthenticatedHttpClient's own doc comment for what
    // it does and why it's a standalone function rather than being inlined here.
    single<HttpClient> {
        createAuthenticatedHttpClient(
            publicClient = get(publicHttpClientQualifier),
            baseUrl = get<AppConfig>().baseUrl,
            sessionRepository = get(),
            sessionEventBus = get()
        )
    }

    single<Settings> { Settings() }
    single { SessionEventBus() }
    single<AuthRepository> { AuthRepositoryImpl(get(publicHttpClientQualifier), get()) }
    single<ClubRepository> { ClubRepositoryImpl(get(), get()) }
    single<TeamRepository> { TeamRepositoryImpl(get(), get()) }
    single<PlayerRepository> { PlayerRepositoryImpl(get(), get()) }
    single<AgeGroupPlayerRepository> { AgeGroupPlayerRepositoryImpl(get(), get()) }
    single<SeasonRepository> { SeasonRepositoryImpl(get(), get()) }
    single<AgeGroupRepository> { AgeGroupRepositoryImpl(get(), get()) }
    single<MatchRepository> { MatchRepositoryImpl(get(), get(), get()) }
    single<OpponentRepository> { OpponentRepositoryImpl(get(), get()) }
    single<TrainingPlanRepository> { TrainingPlanRepositoryImpl(get(), get()) }
    single<TrainingSessionRepository> { TrainingSessionRepositoryImpl(get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    single<SessionRepository> { SessionRepositoryImpl(get()) }
    single<UiPreferencesRepository> { UiPreferencesRepositoryImpl(get()) }
    single<FcmTokenRepository> { FcmTokenRepositoryImpl(get(), get()) }
}

val domainModule = module {
    factory { LoginUseCase(get(), get(), get()) }
    factory { RegisterUseCase(get()) }
    factory { RequestPasswordResetUseCase(get()) }
    factory { ResetPasswordUseCase(get()) }
    factory { RequestEmailChangeUseCase(get()) }
    factory { ConfirmEmailChangeUseCase(get(), get()) }
    factory { CheckAndRefreshSessionUseCase(get(), get()) }
    factory { GetClubsUseCase(get()) }
    factory { LogoutUseCase(get(), get(), get()) }
    factory { GetCurrentUserUseCase(get()) }
    factory { UpdateProfileUseCase(get()) }
    factory { ChangePasswordUseCase(get()) }
    factory { RegisterFcmTokenUseCase(get()) }
    factory { RemoveFcmTokenUseCase(get(), get()) }
    factory { RegisterCurrentFcmTokenUseCase(get(), get()) }
    factory { GetClubDetailsUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetClubUseCase(get()) }
    factory { UpdateClubUseCase(get()) }
    factory { GetClubPermissionsUseCase(get()) }
    factory { UpdateClubPermissionsUseCase(get()) }
    factory { GetClubHomeUseCase(get()) }
    factory { GetTeamDetailsUseCase(get()) }
    factory { GetTeamSeasonRecordUseCase(get()) }
    factory { GetPlayerDetailsUseCase(get()) }
    factory { GetPlayerSeasonStatsUseCase(get()) }
    factory { GetSeasonsUseCase(get()) }
    factory { GetAgeGroupsUseCase(get()) }
    factory { GetAgeGroupDetailsUseCase(get()) }
    factory { GetAgeGroupSeasonRecordUseCase(get()) }
    factory { CreateSeasonUseCase(get()) }
    factory { CreateAgeGroupsUseCase(get()) }
    factory { UpdateAgeGroupUseCase(get()) }
    factory { CreateTeamsUseCase(get()) }
    factory { UpdateTeamUseCase(get()) }
    factory { DeleteTeamUseCase(get()) }
    factory { GetEligiblePlayersForAgeGroupUseCase(get()) }
    factory { AddPlayersToAgeGroupUseCase(get()) }
    factory { CreatePlayersUseCase(get()) }
    factory { UpdatePlayerUseCase(get()) }
    factory { GetOpponentsUseCase(get()) }
    factory { GetOpponentListUseCase(get()) }
    factory { GetTrainingPlansUseCase(get()) }
    factory { GetTrainingSessionsUseCase(get()) }
    factory { GetTrainingSessionUseCase(get()) }
    factory { CreateTrainingSessionsUseCase(get()) }
    factory { SaveTrainingSessionUseCase(get()) }
    factory { ChangeTrainingSessionUseCase(get()) }
    factory { TrainingExercisesUseCase(get()) }
    factory { TrainingRatingsUseCase(get()) }
    factory { TrainingStatsUseCase(get()) }
    factory { SaveTrainingPlanUseCase(get()) }
    factory { GetOpponentDetailsUseCase(get()) }
    factory { CreateOpponentUseCase(get()) }
    factory { UpdateOpponentUseCase(get()) }
    factory { CreateMatchUseCase(get()) }
    factory { GetMatchesUseCase(get()) }
    factory { GetMatchDetailsUseCase(get()) }
    factory { UpdateMatchUseCase(get()) }
    factory { DeleteMatchUseCase(get()) }
    factory { GetMatchReportUseCase(get()) }
    factory { SaveMatchReportUseCase(get()) }
    factory { UpdateMatchStatusUseCase(get()) }
    factory { StartMatchUseCase(get()) }
    factory { PauseMatchUseCase(get()) }
    factory { ResumeMatchUseCase(get()) }
    factory { SetMatchMinuteUseCase(get()) }
    factory { ManageCallUpsUseCase(get()) }
    factory { GetMatchEventsUseCase(get()) }
    factory { GetMatchScoreUseCase(get()) }
    factory { AddMatchEventUseCase(get()) }
    factory { DeleteMatchEventUseCase(get()) }
    factory { ObserveLiveMatchUseCase(get()) }
    factory { ObserveClubMatchesUseCase(get()) }
    factory { GetClubMembersUseCase(get()) }
    factory { GetAgeGroupStaffUseCase(get()) }
    factory { AddStaffToAgeGroupUseCase(get()) }
    factory { AddClubMembersUseCase(get()) }
    factory { GetClubMembersOverviewUseCase(get()) }
    factory { UpdateClubMemberRoleUseCase(get()) }
    factory { ResendClubInviteUseCase(get()) }
    factory { CancelClubInviteUseCase(get()) }
    factory { RemoveClubMemberUseCase(get()) }
}

val presentationModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::ChangeEmailViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::SplashViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::AccountViewModel)
    viewModelOf(::OpponentsViewModel)
    viewModelOf(::OpponentFormViewModel)
    viewModelOf(::OpponentDetailsViewModel)
    viewModelOf(::TrainingPlansViewModel)
    viewModelOf(::PlanEditViewModel)
    viewModelOf(::CreateSessionsViewModel)
    viewModelOf(::SessionDetailsViewModel)
    viewModelOf(::SessionEditViewModel)
    viewModelOf(::ExerciseLibraryViewModel)
    viewModelOf(::RatePlayersViewModel)
    viewModelOf(::ClubDetailsViewModel)
    viewModelOf(::ClubPlayersViewModel)
    viewModelOf(::EditClubViewModel)
    viewModelOf(::ClubMemberDetailsViewModel)
    viewModelOf(::CreateSeasonViewModel)
    viewModelOf(::CreateAgeGroupsViewModel)
    viewModelOf(::CreateTeamsViewModel)
    viewModelOf(::EditTeamViewModel)
    viewModelOf(::EditAgeGroupViewModel)
    viewModelOf(::AddPlayersToAgeGroupViewModel)
    viewModelOf(::CreatePlayersViewModel)
    viewModelOf(::AgeGroupDetailsViewModel)
    viewModelOf(::TeamDetailsViewModel)
    viewModelOf(::CreateMatchViewModel)
    viewModelOf(::MatchDetailsViewModel)
    viewModelOf(::MatchReportViewModel)
    viewModelOf(::LiveMatchViewModel)
    viewModelOf(::AddStaffToAgeGroupViewModel)
    viewModelOf(::CreateStaffViewModel)
    viewModelOf(::PlayerDetailsViewModel)
    viewModelOf(::EditPlayerViewModel)
}

/**
 * [appConfig] is provided by each platform's own entry point (Android's SquadifyApplication, Desktop's
 * main(), the web client's main()), constructed from whatever mechanism that platform uses to select an
 * environment (Android product flavors, a Gradle property for Desktop/Web) -- see
 * TECHNICAL_DECISIONS.md's "Environment-Based App Config" section.
 *
 * [pushTokenProvider] defaults to [NoOpPushTokenProvider] -- only Android's entry point overrides it
 * with a real, Firebase-backed implementation for now (see the push-notifications section of
 * TECHNICAL_DECISIONS.md); every other platform needs no change at all to pick up the default.
 */
fun initKoin(
    appConfig: AppConfig,
    pushTokenProvider: PushTokenProvider = NoOpPushTokenProvider,
    appDeclaration: KoinAppDeclaration = {}
) =
    startKoin {
        appDeclaration()
        modules(
            module {
                single<AppConfig> { appConfig }
                single<PushTokenProvider> { pushTokenProvider }
            },
            dataModule, domainModule, presentationModule
        )
    }
