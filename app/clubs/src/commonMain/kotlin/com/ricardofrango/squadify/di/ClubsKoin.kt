package com.ricardofrango.squadify.di

import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.config.AppFeatures
import com.ricardofrango.squadify.domain.service.NoOpPushTokenProvider
import com.ricardofrango.squadify.domain.service.PushTokenProvider
import com.ricardofrango.squadify.presentation.navigation.ClubsSignedInHome
import com.ricardofrango.squadify.presentation.navigation.SignedInHome
import com.ricardofrango.squadify.presentation.ui.addstafftoagegroup.AddStaffToAgeGroupViewModel
import com.ricardofrango.squadify.presentation.ui.clubdetails.ClubDetailsViewModel
import com.ricardofrango.squadify.presentation.ui.clubdetails.ClubPlayersViewModel
import com.ricardofrango.squadify.presentation.ui.clubmember.ClubMemberDetailsViewModel
import com.ricardofrango.squadify.presentation.ui.createseason.CreateSeasonViewModel
import com.ricardofrango.squadify.presentation.ui.createstaff.CreateStaffViewModel
import com.ricardofrango.squadify.presentation.ui.dashboard.DashboardViewModel
import com.ricardofrango.squadify.presentation.ui.editclub.EditClubViewModel
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** The clubs app's own screens: the club list, the club's home and settings, members, seasons, staff. */
val clubsModule = module {
    single<SignedInHome> { ClubsSignedInHome(get(), get()) }
    // The clubs app has everything the shared screens offer.
    single { AppFeatures(training = true, seasons = true, photos = true, clubMembers = true) }
    viewModelOf(::DashboardViewModel)
    viewModelOf(::ClubDetailsViewModel)
    viewModelOf(::ClubPlayersViewModel)
    viewModelOf(::EditClubViewModel)
    viewModelOf(::ClubMemberDetailsViewModel)
    viewModelOf(::CreateSeasonViewModel)
    viewModelOf(::AddStaffToAgeGroupViewModel)
    viewModelOf(::CreateStaffViewModel)
}

/** The clubs app's Koin: the shared modules plus [clubsModule]. Every clubs entry point calls it. */
fun initKoin(
    appConfig: AppConfig,
    pushTokenProvider: PushTokenProvider = NoOpPushTokenProvider,
    appDeclaration: KoinAppDeclaration = {}
) = startSquadifyKoin(appConfig, pushTokenProvider, listOf(clubsModule), appDeclaration)
