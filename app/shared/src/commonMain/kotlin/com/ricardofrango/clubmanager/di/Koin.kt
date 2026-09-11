package com.ricardofrango.clubmanager.di

import com.ricardofrango.clubmanager.data.config.DevAppConfig
import com.ricardofrango.clubmanager.data.local.SessionRepositoryImpl
import com.ricardofrango.clubmanager.data.remote.*
import com.ricardofrango.clubmanager.domain.config.AppConfig
import com.ricardofrango.clubmanager.domain.repository.*
import com.ricardofrango.clubmanager.domain.usecase.*
import com.ricardofrango.clubmanager.presentation.ui.account.AccountViewModel
import com.ricardofrango.clubmanager.presentation.ui.clubdetails.ClubDetailsViewModel
import com.ricardofrango.clubmanager.presentation.ui.createseason.CreateSeasonViewModel
import com.ricardofrango.clubmanager.presentation.ui.createagegroups.CreateAgeGroupsViewModel
import com.ricardofrango.clubmanager.presentation.ui.createteams.CreateTeamsViewModel
import com.ricardofrango.clubmanager.presentation.ui.addplayerstoagegroup.AddPlayersToAgeGroupViewModel
import com.ricardofrango.clubmanager.presentation.ui.createplayers.CreatePlayersViewModel
import com.ricardofrango.clubmanager.presentation.ui.agegroupdetails.AgeGroupDetailsViewModel
import com.ricardofrango.clubmanager.presentation.ui.teamdetails.TeamDetailsViewModel
import com.ricardofrango.clubmanager.presentation.ui.creatematch.CreateMatchViewModel
import com.ricardofrango.clubmanager.presentation.ui.matchdetails.MatchDetailsViewModel
import com.ricardofrango.clubmanager.presentation.ui.livematch.LiveMatchViewModel
import com.ricardofrango.clubmanager.presentation.ui.addstafftoagegroup.AddStaffToAgeGroupViewModel
import com.ricardofrango.clubmanager.presentation.ui.createstaff.CreateStaffViewModel
import com.ricardofrango.clubmanager.presentation.ui.playerdetails.PlayerDetailsViewModel
import com.ricardofrango.clubmanager.presentation.ui.dashboard.DashboardViewModel
import com.ricardofrango.clubmanager.presentation.ui.login.LoginViewModel
import com.ricardofrango.clubmanager.presentation.ui.register.RegisterViewModel
import com.ricardofrango.clubmanager.presentation.ui.splash.SplashViewModel
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
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val dataModule = module {
    single<AppConfig> { DevAppConfig() }

    single {
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

    single<Settings> { Settings() }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<ClubRepository> { ClubRepositoryImpl(get(), get(), get()) }
    single<TeamRepository> { TeamRepositoryImpl(get(), get(), get()) }
    single<PlayerRepository> { PlayerRepositoryImpl(get(), get(), get()) }
    single<AgeGroupPlayerRepository> { AgeGroupPlayerRepositoryImpl(get(), get(), get()) }
    single<SeasonRepository> { SeasonRepositoryImpl(get(), get(), get()) }
    single<AgeGroupRepository> { AgeGroupRepositoryImpl(get(), get(), get()) }
    single<MatchRepository> { MatchRepositoryImpl(get(), get(), get()) }
    single<OpponentRepository> { OpponentRepositoryImpl(get(), get(), get()) }
    single<SessionRepository> { SessionRepositoryImpl(get()) }
}

val domainModule = module {
    factory { LoginUseCase(get(), get()) }
    factory { RegisterUseCase(get()) }
    factory { CheckAndRefreshSessionUseCase(get(), get()) }
    factory { GetClubsUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { GetClubDetailsUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetClubHomeUseCase(get()) }
    factory { GetTeamDetailsUseCase(get()) }
    factory { GetPlayerDetailsUseCase(get()) }
    factory { GetSeasonsUseCase(get()) }
    factory { GetAgeGroupsUseCase(get()) }
    factory { GetAgeGroupDetailsUseCase(get()) }
    factory { CreateSeasonUseCase(get()) }
    factory { CreateAgeGroupsUseCase(get()) }
    factory { CreateTeamsUseCase(get()) }
    factory { GetEligiblePlayersForAgeGroupUseCase(get()) }
    factory { AddPlayersToAgeGroupUseCase(get()) }
    factory { CreatePlayersUseCase(get()) }
    factory { GetOpponentsUseCase(get()) }
    factory { CreateOpponentUseCase(get()) }
    factory { CreateMatchUseCase(get()) }
    factory { GetMatchesUseCase(get()) }
    factory { GetMatchDetailsUseCase(get()) }
    factory { UpdateMatchUseCase(get()) }
    factory { UpdateMatchStatusUseCase(get()) }
    factory { StartMatchUseCase(get()) }
    factory { ManageCallUpsUseCase(get()) }
    factory { UpdateCallUpStatusUseCase(get()) }
    factory { GetMatchEventsUseCase(get()) }
    factory { GetMatchScoreUseCase(get()) }
    factory { AddMatchEventUseCase(get()) }
    factory { DeleteMatchEventUseCase(get()) }
    factory { ObserveLiveMatchUseCase(get()) }
    factory { GetClubMembersUseCase(get()) }
    factory { GetAgeGroupStaffUseCase(get()) }
    factory { AddStaffToAgeGroupUseCase(get()) }
    factory { AddClubMembersUseCase(get()) }
}

val presentationModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::SplashViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::AccountViewModel)
    viewModelOf(::ClubDetailsViewModel)
    viewModelOf(::CreateSeasonViewModel)
    viewModelOf(::CreateAgeGroupsViewModel)
    viewModelOf(::CreateTeamsViewModel)
    viewModelOf(::AddPlayersToAgeGroupViewModel)
    viewModelOf(::CreatePlayersViewModel)
    viewModelOf(::AgeGroupDetailsViewModel)
    viewModelOf(::TeamDetailsViewModel)
    viewModelOf(::CreateMatchViewModel)
    viewModelOf(::MatchDetailsViewModel)
    viewModelOf(::LiveMatchViewModel)
    viewModelOf(::AddStaffToAgeGroupViewModel)
    viewModelOf(::CreateStaffViewModel)
    viewModelOf(::PlayerDetailsViewModel)
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(dataModule, domainModule, presentationModule)
    }
