package com.ricardofrango.squadify.coach.di

import com.ricardofrango.squadify.coach.data.AccountDeletionRepositoryImpl
import com.ricardofrango.squadify.coach.data.SpaceRepositoryImpl
import com.ricardofrango.squadify.coach.domain.AccountDeletionRepository
import com.ricardofrango.squadify.coach.domain.DeleteAccountUseCase
import com.ricardofrango.squadify.coach.domain.GetAccountDeletionSummaryUseCase
import com.ricardofrango.squadify.coach.presentation.ui.deleteaccount.DeleteAccountViewModel
import com.ricardofrango.squadify.coach.data.StaffInviteRepositoryImpl
import com.ricardofrango.squadify.coach.domain.CreateStaffInviteUseCase
import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
import com.ricardofrango.squadify.coach.domain.LoadPlansUseCase
import com.ricardofrango.squadify.coach.domain.RequestPlanUseCase
import com.ricardofrango.squadify.coach.domain.OwnSpacePlan
import com.ricardofrango.squadify.coach.domain.GetStaffInvitesUseCase
import com.ricardofrango.squadify.coach.domain.JoinWithCodeUseCase
import com.ricardofrango.squadify.coach.domain.RevokeStaffInviteUseCase
import com.ricardofrango.squadify.coach.domain.SetUpSpaceUseCase
import com.ricardofrango.squadify.coach.domain.SpaceRepository
import com.ricardofrango.squadify.coach.domain.StaffInviteRepository
import com.ricardofrango.squadify.coach.presentation.navigation.CoachSignedInHome
import com.ricardofrango.squadify.coach.presentation.ui.home.CoachHomeViewModel
import com.ricardofrango.squadify.coach.presentation.ui.invite.InviteStaffViewModel
import com.ricardofrango.squadify.coach.presentation.ui.join.JoinWithCodeViewModel
import com.ricardofrango.squadify.coach.presentation.ui.space.MySpaceViewModel
import com.ricardofrango.squadify.coach.presentation.ui.spacesetup.SpaceSetupViewModel
import com.ricardofrango.squadify.di.startSquadifyKoin
import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.config.AppFeatures
import com.ricardofrango.squadify.domain.service.NoOpPushTokenProvider
import com.ricardofrango.squadify.domain.service.PushTokenProvider
import com.ricardofrango.squadify.presentation.navigation.SignedInHome
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** The coach app's own pieces: the spaces, the home, joining with a code, invites, and where sign-in leads. */
val coachModule = module {
    single<SpaceRepository> { SpaceRepositoryImpl(get(), get()) }
    single<StaffInviteRepository> { StaffInviteRepositoryImpl(get(), get()) }
    single<AccountDeletionRepository> { AccountDeletionRepositoryImpl(get(), get()) }
    factory { GetAccountDeletionSummaryUseCase(get()) }
    factory { DeleteAccountUseCase(get()) }
    single { OwnSpacePlan() }
    factory { GetSpacesUseCase(get(), get(), get()) }
    factory { LoadPlansUseCase(get(), get()) }
    factory { RequestPlanUseCase(get(), get(), get()) }
    factory { SetUpSpaceUseCase(get()) }
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
    single { AppFeatures(training = true, seasons = false, photos = true, clubMembers = false, accountDeletion = true) }
    viewModelOf(::SpaceSetupViewModel)
    viewModelOf(::CoachHomeViewModel)
    viewModelOf(::JoinWithCodeViewModel)
    viewModelOf(::InviteStaffViewModel)
    viewModelOf(::DeleteAccountViewModel)
    viewModelOf(::MySpaceViewModel)
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
