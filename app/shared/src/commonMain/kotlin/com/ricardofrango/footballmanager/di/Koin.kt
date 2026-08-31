package com.ricardofrango.footballmanager.di

import com.ricardofrango.footballmanager.data.config.DevAppConfig
import com.ricardofrango.footballmanager.data.config.ProdAppConfig
import com.ricardofrango.footballmanager.data.remote.AuthRepositoryImpl
import com.ricardofrango.footballmanager.domain.config.AppConfig
import com.ricardofrango.footballmanager.domain.repository.AuthRepository
import com.ricardofrango.footballmanager.domain.usecase.LoginUseCase
import com.ricardofrango.footballmanager.presentation.ui.login.LoginViewModel
import com.ricardofrango.footballmanager.presentation.ui.register.RegisterViewModel
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val dataModule = module {
    // For now manually choosing DevAppConfig. 
    // In real app, this could be passed via appDeclaration based on build type.
    single<AppConfig> { DevAppConfig() }
    single<AuthRepository> { AuthRepositoryImpl(get()) }
}

val domainModule = module {
    factory { LoginUseCase(get()) }
}

val presentationModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(dataModule, domainModule, presentationModule)
    }
