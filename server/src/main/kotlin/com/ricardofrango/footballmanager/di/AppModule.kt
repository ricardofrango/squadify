package com.ricardofrango.footballmanager.di

import com.ricardofrango.footballmanager.data.repository.ExposedClubRepository
import com.ricardofrango.footballmanager.data.repository.ExposedUserRepository
import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.usecase.CreateClubUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetClubsUseCase
import com.ricardofrango.footballmanager.domain.usecase.LoginUseCase
import com.ricardofrango.footballmanager.domain.usecase.RegisterUseCase
import org.koin.dsl.module

val appModule = module {
    single<ClubRepository> { ExposedClubRepository() }
    single<UserRepository> { ExposedUserRepository() }
    
    factory { GetClubsUseCase(get()) }
    factory { CreateClubUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { LoginUseCase(get()) }
}
