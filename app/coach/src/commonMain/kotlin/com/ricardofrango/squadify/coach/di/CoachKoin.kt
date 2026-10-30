package com.ricardofrango.squadify.coach.di

import com.ricardofrango.squadify.coach.data.SpaceRepositoryImpl
import com.ricardofrango.squadify.coach.domain.GetSpaceUseCase
import com.ricardofrango.squadify.coach.domain.SetUpSpaceUseCase
import com.ricardofrango.squadify.coach.domain.SpaceRepository
import com.ricardofrango.squadify.coach.presentation.navigation.CoachSignedInHome
import com.ricardofrango.squadify.coach.presentation.ui.home.CoachHomeViewModel
import com.ricardofrango.squadify.coach.presentation.ui.spacesetup.SpaceSetupViewModel
import com.ricardofrango.squadify.di.startSquadifyKoin
import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.service.NoOpPushTokenProvider
import com.ricardofrango.squadify.domain.service.PushTokenProvider
import com.ricardofrango.squadify.presentation.navigation.SignedInHome
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** The coach app's own pieces: the space, its first setup and home, and where sign-in leads. */
val coachModule = module {
    single<SpaceRepository> { SpaceRepositoryImpl(get(), get()) }
    factory { GetSpaceUseCase(get()) }
    factory { SetUpSpaceUseCase(get()) }
    single<SignedInHome> { CoachSignedInHome(get()) }
    viewModelOf(::SpaceSetupViewModel)
    viewModelOf(::CoachHomeViewModel)
}

/**
 * The coach app's Koin: the shared modules plus [coachModule], against the coach server
 * ([AppConfig.baseUrl]). No pushes yet: the coach server sends none (TECHNICAL_DECISIONS §72).
 */
fun initCoachKoin(
    appConfig: AppConfig,
    pushTokenProvider: PushTokenProvider = NoOpPushTokenProvider,
    appDeclaration: KoinAppDeclaration = {}
) = startSquadifyKoin(appConfig, pushTokenProvider, listOf(coachModule), appDeclaration)
