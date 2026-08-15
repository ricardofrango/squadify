package com.ricardofrango.footballmanager.di

import com.ricardofrango.footballmanager.data.repository.ExposedClubRepository
import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.usecase.CreateClubUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetClubsUseCase
import org.koin.dsl.module

val appModule = module {
    single<ClubRepository> { ExposedClubRepository() }
    
    factory { GetClubsUseCase(get()) }
    factory { CreateClubUseCase(get()) }
}
