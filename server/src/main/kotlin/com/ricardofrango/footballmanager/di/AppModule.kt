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
    single<InviteRepository> { ExposedInviteRepository() }
    single<MatchRepository> { ExposedMatchRepository() }
    single<MatchEventRepository> { ExposedMatchEventRepository() }
    single<OpponentRepository> { ExposedOpponentRepository() }

    // Use Cases
    factory { GetClubsUseCase(get(), get()) }
    factory { GetClubDetailsUseCase(get(), get()) }
    factory { CreateClubUseCase(get(), get()) }
    factory { ProvisionClubUseCase(get(), get(), get(), get()) }
    factory { RegisterUseCase(get(), get(), get(), get()) }
    factory { LoginUseCase(get()) }
    factory { RefreshTokenUseCase(get()) }
    factory { CheckUserPermissionUseCase(get(), get()) }
    factory { CheckUserIsAdminUseCase(get()) }
    factory { AddClubMemberUseCase(get(), get(), get(), get()) }
    factory { UpdateClubMemberRoleUseCase(get(), get()) }

    factory { CreateTeamUseCase(get(), get()) }
    factory { UpdateTeamStatusUseCase(get(), get()) }
    factory { AddStaffToTeamUseCase(get(), get(), get()) }
    factory { GetTeamStaffUseCase(get(), get()) }
    factory { GetTeamPlayersUseCase(get(), get(), get()) }
    factory { GetTeamDetailsUseCase(get(), get(), get()) }
    factory { GetSeasonsUseCase(get(), get()) }
    factory { CreateSeasonUseCase(get(), get()) }
    factory { UpdateSeasonStatusUseCase(get(), get()) }
    factory { GetAgeGroupsUseCase(get(), get()) }
    factory { CreateAgeGroupUseCase(get(), get()) }
    factory { GetPlayerUseCase(get(), get()) }
    factory { GetPlayersUseCase(get(), get()) }
    factory { CreatePlayerUseCase(get(), get(), get(), get(), get()) }
    factory { CreatePlayersUseCase(get(), get()) }
    factory { UpdatePlayerStatusUseCase(get(), get()) }
    factory { UpdatePlayerUseCase(get(), get(), get(), get(), get()) }
    factory { AddPlayerToTeamUseCase(get(), get(), get(), get()) }
    factory { AddPlayersToTeamUseCase(get(), get(), get(), get()) }
    factory { GetTeamsUseCase(get(), get()) }
    factory { CreateMatchUseCase(get(), get(), get(), get()) }
    factory { GetMatchesUseCase(get(), get()) }
    factory { GetMatchDetailsUseCase(get(), get(), get()) }
    factory { UpdateMatchStatusUseCase(get(), get(), get()) }
    factory { ManageCallUpsUseCase(get(), get()) }
    factory { UpdateCallUpStatusUseCase(get(), get()) }
    factory { CreateOpponentUseCase(get(), get()) }
    factory { GetOpponentsUseCase(get(), get()) }
    factory { AddMatchEventUseCase(get(), get(), get()) }
    factory { GetMatchEventsUseCase(get(), get(), get()) }
    factory { GetMatchScoreUseCase(get(), get(), get()) }
}
