package com.ricardofrango.footballmanager.di

import com.ricardofrango.footballmanager.data.config.DevAppConfig
import com.ricardofrango.footballmanager.data.local.SessionRepositoryImpl
import com.ricardofrango.footballmanager.data.remote.*
import com.ricardofrango.footballmanager.domain.config.AppConfig
import com.ricardofrango.footballmanager.domain.repository.*
import com.ricardofrango.footballmanager.domain.usecase.*
import com.ricardofrango.footballmanager.presentation.ui.account.AccountViewModel
import com.ricardofrango.footballmanager.presentation.ui.clubdetails.ClubDetailsViewModel
import com.ricardofrango.footballmanager.presentation.ui.createseason.CreateSeasonViewModel
import com.ricardofrango.footballmanager.presentation.ui.teamdetails.TeamDetailsViewModel
import com.ricardofrango.footballmanager.presentation.ui.playerdetails.PlayerDetailsViewModel
import com.ricardofrango.footballmanager.presentation.ui.dashboard.DashboardViewModel
import com.ricardofrango.footballmanager.presentation.ui.login.LoginViewModel
import com.ricardofrango.footballmanager.presentation.ui.register.RegisterViewModel
import com.ricardofrango.footballmanager.presentation.ui.splash.SplashViewModel
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
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
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                })
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
    single<TeamPlayerRepository> { TeamPlayerRepositoryImpl(get(), get(), get()) }
    single<SeasonRepository> { SeasonRepositoryImpl(get(), get(), get()) }
    single<AgeGroupRepository> { AgeGroupRepositoryImpl(get(), get(), get()) }
    single<SessionRepository> { SessionRepositoryImpl(get()) }
}

val domainModule = module {
    factory { LoginUseCase(get(), get()) }
    factory { RegisterUseCase(get()) }
    factory { CheckAndRefreshSessionUseCase(get(), get()) }
    factory { GetClubsUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { GetClubDetailsUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetTeamDetailsUseCase(get()) }
    factory { GetPlayerDetailsUseCase(get()) }
    factory { GetSeasonsUseCase(get()) }
    factory { CreateSeasonUseCase(get()) }
}

val presentationModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::SplashViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::AccountViewModel)
    viewModelOf(::ClubDetailsViewModel)
    viewModelOf(::CreateSeasonViewModel)
    viewModelOf(::TeamDetailsViewModel)
    viewModelOf(::PlayerDetailsViewModel)
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(dataModule, domainModule, presentationModule)
    }
