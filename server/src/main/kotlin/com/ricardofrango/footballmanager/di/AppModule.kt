package com.ricardofrango.footballmanager.di

import com.ricardofrango.footballmanager.config.ServerConfig
import com.ricardofrango.footballmanager.data.database.DatabaseFactory
import com.ricardofrango.footballmanager.data.repository.*
import com.ricardofrango.footballmanager.data.service.KotlinFlowMatchBus
import com.ricardofrango.footballmanager.domain.repository.*
import com.ricardofrango.footballmanager.domain.service.LiveMatchBus
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.domain.usecase.*
import org.koin.dsl.module

val appModule = module {
    single<ClubRepository> { ExposedClubRepository() }
    single<UserRepository> { ExposedUserRepository() }
    single<TeamRepository> { ExposedTeamRepository() }
    single<SeasonRepository> { ExposedSeasonRepository() }
    single<AgeGroupRepository> { ExposedAgeGroupRepository() }
    single<PlayerRepository> { ExposedPlayerRepository() }
    single<InviteRepository> { ExposedInviteRepository() }
    single<MatchRepository> { ExposedMatchRepository() }
    single<MatchEventRepository> { ExposedMatchEventRepository() }
    single<OpponentRepository> { ExposedOpponentRepository() }

    // Services
    single<LiveMatchBus> { KotlinFlowMatchBus() }
    single<TransactionManager> { DatabaseFactory }

    // Use Cases
    factory { GetClubsUseCase(get(), get(), get()) }
    factory { GetClubDetailsUseCase(get(), get(), get()) }
    factory { ProvisionClubUseCase(get(), get(), get(), get(), get()) }
    factory { RegisterUseCase(get(), get(), get(), get(), get()) }
    factory {
        SeedPlatformAdminUseCase(
            get(),
            get(),
            enabled = ServerConfig.seedAdminEnabled,
            adminEmail = ServerConfig.seedAdminEmail,
            adminPassword = ServerConfig.seedAdminPassword,
        )
    }
    factory { LoginUseCase(get(), get()) }
    factory { RefreshTokenUseCase(get(), get()) }
    factory { CheckUserPermissionUseCase(get(), get(), get(), get(), get()) }
    factory { CheckUserIsAdminUseCase(get(), get()) }
    factory { AddClubMembersUseCase(get(), get(), get(), get(), get()) }
    factory { UpdateClubMemberRoleUseCase(get(), get(), get()) }
    factory { UpdateClubUseCase(get(), get(), get()) }

    factory { CreateTeamUseCase(get(), get(), get(), get()) }
    factory { CreateTeamsUseCase(get(), get(), get(), get()) }
    factory { AddStaffToAgeGroupUseCase(get(), get(), get(), get()) }
    factory { GetAgeGroupStaffUseCase(get(), get(), get()) }
    factory { GetTeamPlayersUseCase(get(), get(), get(), get()) }
    factory { GetTeamDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { UpdateTeamUseCase(get(), get(), get()) }

    factory { GetSeasonsUseCase(get(), get(), get(), get(), get(), get()) }
    factory { CreateSeasonUseCase(get(), get(), get()) }
    factory { UpdateSeasonUseCase(get(), get(), get()) }

    factory { GetAgeGroupsUseCase(get(), get(), get(), get(), get()) }
    factory { CreateAgeGroupsUseCase(get(), get(), get()) }
    factory { UpdateAgeGroupUseCase(get(), get(), get()) }
    factory { GetAgeGroupDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    factory { GetPlayerUseCase(get(), get(), get()) }
    factory { GetPlayersUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetAgeGroupPlayersByClubUseCase(get(), get(), get()) }
    factory { CreatePlayersUseCase(get(), get(), get(), get(), get(), get()) }
    factory { UpdatePlayerUseCase(get(), get(), get(), get(), get(), get()) }
    factory { AddPlayersToAgeGroupUseCase(get(), get(), get(), get()) }
    factory { GetEligiblePlayersForAgeGroupUseCase(get(), get(), get(), get()) }

    factory { GetTeamsUseCase(get(), get(), get(), get(), get(), get()) }
    factory { CreateMatchUseCase(get(), get(), get(), get(), get()) }
    factory { GetMatchesUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { GetMatchDetailsUseCase(get(), get(), get(), get()) }
    factory { UpdateMatchStatusUseCase(get(), get(), get(), get(), get()) }
    factory { ManageCallUpsUseCase(get(), get(), get()) }
    factory { UpdateCallUpStatusUseCase(get(), get(), get()) }
    factory { CreateOpponentUseCase(get(), get(), get()) }
    factory { GetOpponentsUseCase(get(), get(), get()) }
    factory { UpdateOpponentUseCase(get(), get(), get()) }

    factory { AddMatchEventUseCase(get(), get(), get(), get(), get()) }
    factory { GetMatchEventsUseCase(get(), get(), get(), get()) }
    factory { GetMatchScoreUseCase(get(), get(), get(), get()) }
    factory { GetLiveMatchUpdatesUseCase(get(), get(), get(), get()) }

    // Dashboards
    factory { GetSuperAdminDashboardUseCase(get(), get(), get(), get()) }
    factory { GetClubAdminDashboardUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { GetParentDashboardUseCase(get(), get(), get(), get()) }
}
