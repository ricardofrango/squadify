package com.ricardofrango.footballmanager.di

import com.ricardofrango.footballmanager.data.repository.ExposedClubRepository
import com.ricardofrango.footballmanager.data.repository.ExposedTeamRepository
import com.ricardofrango.footballmanager.data.repository.ExposedUserRepository
import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.usecase.*
import org.koin.dsl.module

val appModule = module {
    single<ClubRepository> { ExposedClubRepository() }
    single<UserRepository> { ExposedUserRepository() }
    single<TeamRepository> { ExposedTeamRepository() }
    
    factory { GetClubsUseCase(get()) }
    factory { CreateClubUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { LoginUseCase(get()) }
    factory { RefreshTokenUseCase(get()) }
    factory { CheckClubPermissionUseCase(get()) }
    factory { CheckTeamPermissionUseCase(get()) }
    factory { CreateTeamUseCase(get(), get()) }
    factory { ManageTeamUseCase(get(), get()) }
}
