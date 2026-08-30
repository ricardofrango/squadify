package com.ricardofrango.footballmanager.di

import com.ricardofrango.footballmanager.data.repository.ExposedAgeGroupRepository
import com.ricardofrango.footballmanager.data.repository.ExposedClubRepository
import com.ricardofrango.footballmanager.data.repository.ExposedInviteRepository
import com.ricardofrango.footballmanager.data.repository.ExposedMatchEventRepository
import com.ricardofrango.footballmanager.data.repository.ExposedMatchRepository
import com.ricardofrango.footballmanager.data.repository.ExposedOpponentRepository
import com.ricardofrango.footballmanager.data.repository.ExposedPlayerRepository
import com.ricardofrango.footballmanager.data.repository.ExposedSeasonRepository
import com.ricardofrango.footballmanager.data.repository.ExposedTeamRepository
import com.ricardofrango.footballmanager.data.repository.ExposedUserRepository
import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.InviteRepository
import com.ricardofrango.footballmanager.domain.repository.MatchEventRepository
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.usecase.AddMatchEventUseCase
import com.ricardofrango.footballmanager.domain.usecase.AddPlayerToTeamUseCase
import com.ricardofrango.footballmanager.domain.usecase.AddPlayersToTeamUseCase
import com.ricardofrango.footballmanager.domain.usecase.AddStaffToTeamUseCase
import com.ricardofrango.footballmanager.domain.usecase.CheckUserIsAdminUseCase
import com.ricardofrango.footballmanager.domain.usecase.CheckUserPermissionUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreateAgeGroupUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreateClubUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreateMatchUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreateOpponentUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreatePlayerUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreatePlayersUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreateSeasonUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreateTeamUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetClubDetailsUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetClubsUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetMatchDetailsUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetMatchEventsUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetMatchScoreUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetMatchesUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetOpponentsUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetPlayerUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetPlayersUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetSeasonsUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetTeamDetailsUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetTeamPlayersUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetTeamStaffUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetTeamsUseCase
import com.ricardofrango.footballmanager.domain.usecase.LoginUseCase
import com.ricardofrango.footballmanager.domain.usecase.ManageCallUpsUseCase
import com.ricardofrango.footballmanager.domain.usecase.ProvisionClubUseCase
import com.ricardofrango.footballmanager.domain.usecase.RefreshTokenUseCase
import com.ricardofrango.footballmanager.domain.usecase.RegisterUseCase
import com.ricardofrango.footballmanager.domain.usecase.UpdateCallUpStatusUseCase
import com.ricardofrango.footballmanager.domain.usecase.UpdateMatchStatusUseCase
import com.ricardofrango.footballmanager.domain.usecase.UpdatePlayerStatusUseCase
import com.ricardofrango.footballmanager.domain.usecase.UpdatePlayerUseCase
import com.ricardofrango.footballmanager.domain.usecase.UpdateSeasonStatusUseCase
import com.ricardofrango.footballmanager.domain.usecase.UpdateTeamStatusUseCase
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
    factory { CreatePlayerUseCase(get(), get(), get(), get()) }
    factory { CreatePlayersUseCase(get(), get()) }
    factory { UpdatePlayerStatusUseCase(get(), get()) }
    factory { UpdatePlayerUseCase(get(), get(), get(), get()) }
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
