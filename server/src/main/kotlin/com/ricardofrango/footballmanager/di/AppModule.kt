package com.ricardofrango.footballmanager.di

import com.ricardofrango.footballmanager.data.repository.*
import com.ricardofrango.footballmanager.domain.repository.*
import com.ricardofrango.footballmanager.domain.usecase.*
import org.koin.dsl.module

val appModule = module {
    single<ClubRepository> { ExposedClubRepository() }
    single<UserRepository> { ExposedUserRepository() }
    single<TeamRepository> { ExposedTeamRepository() }
    single<SeasonRepository> { ExposedSeasonRepository() }
    single<AgeGroupRepository> { ExposedAgeGroupRepository() }
    single<PlayerRepository> { ExposedPlayerRepository() }
    
    factory { GetClubsUseCase(get(), get()) }
    factory { GetClubDetailsUseCase(get(), get()) }
    factory { CreateClubUseCase(get()) }
    factory { ProvisionClubUseCase(get(), get()) }
    factory { RegisterUseCase(get()) }
    factory { LoginUseCase(get()) }
    factory { RefreshTokenUseCase(get()) }
    factory { CheckClubPermissionUseCase(get()) }
    factory { CheckTeamPermissionUseCase(get()) }
    factory { CreateTeamUseCase(get(), get()) }
    factory { ManageTeamUseCase(get(), get()) }
    factory { AddStaffToTeamUseCase(get(), get()) }
    factory { GetTeamStaffUseCase(get()) }
    factory { GetSeasonsUseCase(get()) }
    factory { CreateSeasonUseCase(get(), get()) }
    factory { GetAgeGroupsUseCase(get()) }
    factory { CreateAgeGroupUseCase(get(), get()) }
    factory { GetPlayerUseCase(get()) }
    factory { GetPlayersUseCase(get()) }
    factory { CreatePlayerUseCase(get(), get()) }
    factory { CreatePlayersUseCase(get(), get()) }
    factory { AddPlayerToTeamUseCase(get(), get(), get(), get()) }
    factory { AddPlayersToTeamUseCase(get(), get(), get(), get()) }
    factory { GetTeamsUseCase(get()) }
}
