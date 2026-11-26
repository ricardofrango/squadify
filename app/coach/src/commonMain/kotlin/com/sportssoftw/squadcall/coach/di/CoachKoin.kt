package com.sportssoftw.squadcall.coach.di

import com.sportssoftw.squadcall.domain.repository.AgeGroupImportRepository
import com.sportssoftw.squadcall.domain.repository.FeatureFlagsRepository
import com.sportssoftw.squadcall.data.remote.FeatureFlagsRepositoryImpl
import com.sportssoftw.squadcall.domain.config.FeatureFlagsStore
import com.sportssoftw.squadcall.data.remote.AgeGroupImportRepositoryImpl
import com.sportssoftw.squadcall.coach.domain.CoachAds
import com.sportssoftw.squadcall.coach.data.AccountDeletionRepositoryImpl
import com.sportssoftw.squadcall.coach.data.SpaceRepositoryImpl
import com.sportssoftw.squadcall.coach.domain.AccountDeletionRepository
import com.sportssoftw.squadcall.coach.domain.DeleteAccountUseCase
import com.sportssoftw.squadcall.coach.domain.GetAccountDeletionSummaryUseCase
import com.sportssoftw.squadcall.coach.presentation.ui.deleteaccount.DeleteAccountViewModel
import com.sportssoftw.squadcall.coach.data.StaffInviteRepositoryImpl
import com.sportssoftw.squadcall.coach.domain.CreateStaffInviteUseCase
import com.sportssoftw.squadcall.coach.domain.AddAgeGroupsUseCase
import com.sportssoftw.squadcall.coach.domain.GetSpaceDetailsUseCase
import com.sportssoftw.squadcall.coach.domain.CheckAgeGroupRoomUseCase
import com.sportssoftw.squadcall.coach.domain.DeleteSpaceUseCase
import com.sportssoftw.squadcall.coach.domain.LeaveSpaceUseCase
import com.sportssoftw.squadcall.coach.domain.GetSpacesUseCase
import com.sportssoftw.squadcall.coach.domain.LoadPlansUseCase
import com.sportssoftw.squadcall.coach.domain.RequestPlanUseCase
import com.sportssoftw.squadcall.coach.domain.SaveStorePurchaseUseCase
import com.sportssoftw.squadcall.coach.domain.OwnSpacePlan
import com.sportssoftw.squadcall.coach.domain.GetStaffInvitesUseCase
import com.sportssoftw.squadcall.coach.domain.JoinWithCodeUseCase
import com.sportssoftw.squadcall.coach.domain.RevokeStaffInviteUseCase
import com.sportssoftw.squadcall.coach.domain.SpaceRepository
import com.sportssoftw.squadcall.coach.domain.StaffInviteRepository
import com.sportssoftw.squadcall.coach.presentation.navigation.CoachSignedInHome
import com.sportssoftw.squadcall.coach.presentation.ui.agegroups.AddAgeGroupsViewModel
import com.sportssoftw.squadcall.coach.presentation.ui.home.CoachHomeViewModel
import com.sportssoftw.squadcall.coach.presentation.ui.invite.InviteStaffViewModel
import com.sportssoftw.squadcall.coach.presentation.ui.join.JoinWithCodeViewModel
import com.sportssoftw.squadcall.coach.presentation.ui.space.MySpaceViewModel
import com.sportssoftw.squadcall.coach.presentation.ui.space.SpaceDetailsViewModel
import com.sportssoftw.squadcall.di.startSquadcallKoin
import com.sportssoftw.squadcall.domain.config.AppConfig
import com.sportssoftw.squadcall.domain.config.AppFeatures
import com.sportssoftw.squadcall.domain.service.NoOpPushTokenProvider
import com.sportssoftw.squadcall.domain.service.PushTokenProvider
import com.sportssoftw.squadcall.presentation.navigation.SignedInHome
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** The coach app's own pieces: the spaces, the home, joining with a code, invites, and where sign-in leads. */
val coachModule = module {
    single<SpaceRepository> { SpaceRepositoryImpl(get(), get()) }
    single<AgeGroupImportRepository> { AgeGroupImportRepositoryImpl(get(), get()) }
    single<FeatureFlagsRepository> { FeatureFlagsRepositoryImpl(get(), get()) }
    single { FeatureFlagsStore(get()) }
    single<StaffInviteRepository> { StaffInviteRepositoryImpl(get(), get()) }
    single<AccountDeletionRepository> { AccountDeletionRepositoryImpl(get(), get()) }
    factory { GetAccountDeletionSummaryUseCase(get()) }
    factory { DeleteAccountUseCase(get()) }
    single { OwnSpacePlan() }
    single { CoachAds() }
    factory { GetSpacesUseCase(get(), get(), get(), get(), plansEnabled = { get<FeatureFlagsStore>().flags.value.plansEnabled }) }
    factory { DeleteSpaceUseCase(get()) }
    factory { LeaveSpaceUseCase(get()) }
    factory { GetSpaceDetailsUseCase(get()) }
    factory { CheckAgeGroupRoomUseCase(get()) }
    factory { AddAgeGroupsUseCase(get()) }
    factory { LoadPlansUseCase(get(), get()) }
    factory { RequestPlanUseCase(get(), get(), get()) }
    factory { SaveStorePurchaseUseCase(get(), get(), get()) }
    factory { JoinWithCodeUseCase(get()) }
    factory { GetStaffInvitesUseCase(get()) }
    factory { CreateStaffInviteUseCase(get()) }
    factory { RevokeStaffInviteUseCase(get()) }
    single<SignedInHome> { CoachSignedInHome }
    // Training (2026-11-03): on Pro, decided by the coach server; below it the Training tab shows the plan's box.
    // The developer, 2026-10-30: seasons are automatic; staff join with
    // invite codes, not from club members (the owner invites and removes them in Age Group Details).
    // Photos and logos (2026-11-02): the coach server's image storage.
    // Account deletion: the stores require it for an app where people sign up.
    single { AppFeatures(training = true, seasons = false, photos = true, clubMembers = false, accountDeletion = true, spaces = true) }
    viewModelOf(::AddAgeGroupsViewModel)
    viewModelOf(::CoachHomeViewModel)
    viewModelOf(::JoinWithCodeViewModel)
    viewModelOf(::InviteStaffViewModel)
    viewModelOf(::DeleteAccountViewModel)
    viewModelOf(::MySpaceViewModel)
    viewModelOf(::SpaceDetailsViewModel)
}

/**
 * The coach app's Koin: the shared modules plus [coachModule], against the coach server
 * ([AppConfig.baseUrl]). No pushes yet: the coach server sends none (TECHNICAL_DECISIONS §72).
 */
fun initCoachKoin(
    appConfig: AppConfig,
    pushTokenProvider: PushTokenProvider = NoOpPushTokenProvider,
    appDeclaration: KoinAppDeclaration = {}
) = startSquadcallKoin(appConfig, pushTokenProvider, listOf(coachModule), appDeclaration)
