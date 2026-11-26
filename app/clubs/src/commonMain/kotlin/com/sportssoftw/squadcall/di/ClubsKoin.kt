package com.sportssoftw.squadcall.di

import com.sportssoftw.squadcall.domain.config.AppConfig
import com.sportssoftw.squadcall.domain.config.AppFeatures
import com.sportssoftw.squadcall.domain.service.NoOpPushTokenProvider
import com.sportssoftw.squadcall.domain.service.PushTokenProvider
import com.sportssoftw.squadcall.presentation.navigation.ClubsSignedInHome
import com.sportssoftw.squadcall.presentation.navigation.SignedInHome
import com.sportssoftw.squadcall.presentation.ui.addstafftoagegroup.AddStaffToAgeGroupViewModel
import com.sportssoftw.squadcall.presentation.ui.clubdetails.ClubDetailsViewModel
import com.sportssoftw.squadcall.presentation.ui.clubdetails.ClubPlayersViewModel
import com.sportssoftw.squadcall.presentation.ui.clubmember.ClubMemberDetailsViewModel
import com.sportssoftw.squadcall.presentation.ui.createseason.CreateSeasonViewModel
import com.sportssoftw.squadcall.presentation.ui.createstaff.CreateStaffViewModel
import com.sportssoftw.squadcall.presentation.ui.dashboard.DashboardViewModel
import com.sportssoftw.squadcall.presentation.ui.editclub.EditClubViewModel
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
) = startSquadcallKoin(appConfig, pushTokenProvider, listOf(clubsModule), appDeclaration)
