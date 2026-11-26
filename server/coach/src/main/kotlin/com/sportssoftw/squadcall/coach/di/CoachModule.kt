package com.sportssoftw.squadcall.coach.di

import com.sportssoftw.squadcall.domain.usecase.CreateExportGrantUseCase
import com.sportssoftw.squadcall.domain.usecase.ExportAgeGroupUseCase
import com.sportssoftw.squadcall.domain.usecase.ImportAgeGroupUseCase
import com.sportssoftw.squadcall.domain.usecase.ListExportableAgeGroupsUseCase
import com.sportssoftw.squadcall.domain.usecase.ListImportableAgeGroupsUseCase
import com.sportssoftw.squadcall.data.repository.ExposedExportGrantRepository
import com.sportssoftw.squadcall.data.repository.ExposedImportRepository
import com.sportssoftw.squadcall.domain.repository.ExportGrantRepository
import com.sportssoftw.squadcall.domain.repository.ImportRepository
import com.sportssoftw.squadcall.coach.data.AccountDeletionRepository
import com.sportssoftw.squadcall.coach.data.CoachEmailService
import com.sportssoftw.squadcall.coach.data.ExposedAccountDeletionRepository
import com.sportssoftw.squadcall.coach.data.ExposedSpacePlanRepository
import com.sportssoftw.squadcall.coach.data.SpacePlanRepository
import com.sportssoftw.squadcall.coach.domain.CoachPlans
import com.sportssoftw.squadcall.coach.domain.GoogleSubscriptions
import com.sportssoftw.squadcall.coach.domain.HandleGoogleNotificationUseCase
import com.sportssoftw.squadcall.coach.domain.VerifyGooglePurchaseUseCase
import com.sportssoftw.squadcall.coach.data.ExposedStoreSubscriptionRepository
import com.sportssoftw.squadcall.coach.data.GooglePlayApi
import com.sportssoftw.squadcall.coach.data.GooglePlayDeveloperApi
import com.sportssoftw.squadcall.coach.data.StoreSubscriptionRepository
import com.sportssoftw.squadcall.model.PlanChange
import com.sportssoftw.squadcall.coach.domain.PlanRequestMailer
import com.sportssoftw.squadcall.coach.domain.RequestPlanUseCase
import com.sportssoftw.squadcall.coach.domain.DeleteAccountUseCase
import com.sportssoftw.squadcall.coach.domain.DeleteSpaceUseCase
import com.sportssoftw.squadcall.coach.domain.LeaveSpaceUseCase
import com.sportssoftw.squadcall.coach.data.ExposedSpaceMembershipRepository
import com.sportssoftw.squadcall.coach.data.SpaceMembershipRepository
import com.sportssoftw.squadcall.coach.domain.GetAccountDeletionSummaryUseCase
import com.sportssoftw.squadcall.coach.domain.GetAdminSpacesUseCase
import com.sportssoftw.squadcall.coach.domain.PlatformAdmins
import com.sportssoftw.squadcall.coach.domain.SetSpacePlanUseCase
import com.sportssoftw.squadcall.coach.domain.StaffInviteMailer
import com.sportssoftw.squadcall.data.repository.ExposedEmailChangeRepository
import com.sportssoftw.squadcall.data.repository.ExposedPasswordResetRepository
import com.sportssoftw.squadcall.domain.repository.EmailChangeRepository
import com.sportssoftw.squadcall.domain.repository.PasswordResetRepository
import com.sportssoftw.squadcall.domain.service.CurrentPasswordGuard
import com.sportssoftw.squadcall.domain.service.EmailService
import com.sportssoftw.squadcall.domain.usecase.ChangePasswordUseCase
import com.sportssoftw.squadcall.domain.usecase.ConfirmEmailChangeUseCase
import com.sportssoftw.squadcall.domain.usecase.GetCurrentUserUseCase
import com.sportssoftw.squadcall.domain.usecase.RequestEmailChangeUseCase
import com.sportssoftw.squadcall.domain.usecase.RequestPasswordResetUseCase
import com.sportssoftw.squadcall.domain.usecase.ResetPasswordUseCase
import com.sportssoftw.squadcall.domain.usecase.UpdateProfileUseCase
import com.sportssoftw.squadcall.coach.access.CoachAccessPolicy
import com.sportssoftw.squadcall.coach.config.CoachConfig
import com.sportssoftw.squadcall.coach.data.CoachDatabase
import com.sportssoftw.squadcall.coach.data.ExposedStaffInviteRepository
import com.sportssoftw.squadcall.coach.data.StaffInviteRepository
import com.sportssoftw.squadcall.coach.access.CoachPushAudience
import com.sportssoftw.squadcall.data.service.FirebaseAdminPushNotificationService
import com.sportssoftw.squadcall.data.service.LocalImageStorage
import com.sportssoftw.squadcall.data.service.S3ImageStorage
import com.sportssoftw.squadcall.data.service.UnavailableImageStorage
import com.sportssoftw.squadcall.domain.service.ImageStorage
import com.sportssoftw.squadcall.domain.service.PrefixedImageStorage
import com.sportssoftw.squadcall.domain.service.PushAudience
import com.sportssoftw.squadcall.domain.usecase.RegisterFcmTokenUseCase
import com.sportssoftw.squadcall.domain.usecase.RemoveFcmTokenUseCase
import com.sportssoftw.squadcall.coach.domain.AcceptStaffInviteUseCase
import com.sportssoftw.squadcall.coach.domain.CoachSeasons
import com.sportssoftw.squadcall.coach.domain.CreateStaffInviteUseCase
import com.sportssoftw.squadcall.coach.domain.GetStaffInvitesUseCase
import com.sportssoftw.squadcall.coach.domain.RemoveStaffUseCase
import com.sportssoftw.squadcall.coach.domain.RevokeStaffInviteUseCase
import com.sportssoftw.squadcall.coach.domain.GetPlansUseCase
import com.sportssoftw.squadcall.coach.domain.AddAgeGroupsUseCase
import com.sportssoftw.squadcall.coach.domain.GetSpaceDetailsUseCase
import com.sportssoftw.squadcall.coach.domain.GetSpacesUseCase
import com.sportssoftw.squadcall.coach.domain.SetUpSpaceUseCase
import com.sportssoftw.squadcall.data.repository.ExposedAgeGroupRepository
import com.sportssoftw.squadcall.data.repository.ExposedClubRepository
import com.sportssoftw.squadcall.data.repository.ExposedFcmTokenRepository
import com.sportssoftw.squadcall.data.repository.ExposedMatchEventRepository
import com.sportssoftw.squadcall.data.repository.ExposedMatchPlayerStatsRepository
import com.sportssoftw.squadcall.data.repository.ExposedMatchReportRepository
import com.sportssoftw.squadcall.data.repository.ExposedMatchRepository
import com.sportssoftw.squadcall.data.repository.ExposedOpponentRepository
import com.sportssoftw.squadcall.data.repository.ExposedPlayerRepository
import com.sportssoftw.squadcall.data.repository.ExposedSeasonRepository
import com.sportssoftw.squadcall.data.repository.ExposedTeamRepository
import com.sportssoftw.squadcall.data.repository.ExposedUserRepository
import com.sportssoftw.squadcall.data.service.KotlinFlowClubMatchesBus
import com.sportssoftw.squadcall.data.service.KotlinFlowMatchBus
import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.FcmTokenRepository
import com.sportssoftw.squadcall.domain.repository.MatchEventRepository
import com.sportssoftw.squadcall.domain.repository.MatchPlayerStatsRepository
import com.sportssoftw.squadcall.domain.repository.MatchReportRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.TrainingRatingRepository
import com.sportssoftw.squadcall.data.repository.ExposedTrainingPlanRepository
import com.sportssoftw.squadcall.data.repository.ExposedTrainingSessionRepository
import com.sportssoftw.squadcall.data.repository.ExposedTrainingExerciseRepository
import com.sportssoftw.squadcall.data.repository.ExposedTrainingRatingRepository
import com.sportssoftw.squadcall.domain.repository.TrainingPlanRepository
import com.sportssoftw.squadcall.domain.repository.TrainingExerciseRepository
import com.sportssoftw.squadcall.domain.usecase.GetTrainingPlansUseCase
import com.sportssoftw.squadcall.domain.usecase.SaveTrainingPlanUseCase
import com.sportssoftw.squadcall.domain.usecase.GetTrainingSessionsUseCase
import com.sportssoftw.squadcall.domain.usecase.CreateTrainingSessionsUseCase
import com.sportssoftw.squadcall.domain.usecase.SaveTrainingSessionUseCase
import com.sportssoftw.squadcall.domain.usecase.ChangeTrainingSessionUseCase
import com.sportssoftw.squadcall.domain.usecase.GetTrainingSessionUseCase
import com.sportssoftw.squadcall.domain.usecase.TrainingRatingsUseCase
import com.sportssoftw.squadcall.domain.usecase.TrainingStatsUseCase
import com.sportssoftw.squadcall.domain.usecase.TrainingExercisesUseCase
import com.sportssoftw.squadcall.domain.repository.TrainingSessionRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.ClubMatchesBus
import com.sportssoftw.squadcall.domain.service.LiveMatchBus
import com.sportssoftw.squadcall.domain.service.PushNotificationService
import com.sportssoftw.squadcall.domain.service.TransactionManager
import java.io.File
import com.sportssoftw.squadcall.domain.usecase.*
import com.sportssoftw.squadcall.security.JwtTokens
import org.koin.dsl.module

/** The coach server's tokens: its own secret and audience (TECHNICAL_DECISIONS §72). */
object CoachTokens : JwtTokens(CoachConfig.jwtSecret, audience = "squadcall-coach-users")

val coachModule = module {
    single<TransactionManager> { CoachDatabase }

    // The shared repositories, on the coach's database. Only the shared tables exist there, so the
    // clubs-only parts (parents' links, invites) stay behind the clubs server's own code.
    single<UserRepository> { ExposedUserRepository() }
    single<ClubRepository> { ExposedClubRepository() }
    single<SeasonRepository> { ExposedSeasonRepository() }
    single<AgeGroupRepository> { ExposedAgeGroupRepository() }
    single<TeamRepository> { ExposedTeamRepository() }
    single<PlayerRepository> { ExposedPlayerRepository() }
    single<OpponentRepository> { ExposedOpponentRepository() }
    single<MatchRepository> { ExposedMatchRepository() }
    single<MatchEventRepository> { ExposedMatchEventRepository() }
    single<FcmTokenRepository> { ExposedFcmTokenRepository() }
    single<ExportGrantRepository> { ExposedExportGrantRepository() }
    single<ImportRepository> { ExposedImportRepository() }
    factory { CreateExportGrantUseCase(get(), get()) }
    factory { ListExportableAgeGroupsUseCase(get(), get(), get(), get(), get(), get()) }
    factory { ListImportableAgeGroupsUseCase(get(), get(), get(), get(), get(), get()) }
    factory { ExportAgeGroupUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), sourceServer = CoachConfig.publicBaseUrl.orEmpty()) }
    factory { ImportAgeGroupUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<MatchPlayerStatsRepository> { ExposedMatchPlayerStatsRepository() }
    single<MatchReportRepository> { ExposedMatchReportRepository() }
    // Training (Pro): the same repositories and use cases as the clubs server.
    single<TrainingPlanRepository> { ExposedTrainingPlanRepository() }
    single<TrainingSessionRepository> { ExposedTrainingSessionRepository() }
    single<TrainingExerciseRepository> { ExposedTrainingExerciseRepository() }
    single<TrainingRatingRepository> { ExposedTrainingRatingRepository() }
    factory { GetTrainingPlansUseCase(get(), get(), get(), get(), get(), get()) }
    factory { SaveTrainingPlanUseCase(get(), get(), get(), get(), get()) }
    factory { GetTrainingSessionsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { CreateTrainingSessionsUseCase(get(), get(), get(), get(), get()) }
    factory { SaveTrainingSessionUseCase(get(), get(), get(), get()) }
    factory { ChangeTrainingSessionUseCase(get(), get(), get(), get()) }
    factory { GetTrainingSessionUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { TrainingRatingsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { TrainingStatsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { TrainingExercisesUseCase(get(), get(), get(), get(), get()) }

    // The live game's updates, to every device following a match or the space's matches.
    single<LiveMatchBus> { KotlinFlowMatchBus() }
    single<ClubMatchesBus> { KotlinFlowClubMatchesBus() }
    // Pushes, through the coach's own Firebase project, to the space's owner and the
    // age group's staff. Its devices' tokens come through the shared fcmTokenRoutes.
    // Its picture is the space's logo, as on the clubs server.
    single<PushNotificationService> {
        val clubRepository = get<ClubRepository>()
        val transactionManager = get<TransactionManager>()
        val matchRepository = get<MatchRepository>()
        val opponentRepository = get<OpponentRepository>()
        val publicImageLinks = get<PublicImageLinks>()
        FirebaseAdminPushNotificationService(CoachConfig.firebaseServiceAccountJson, clubLogoUrl = { spaceId ->
            val logoUrl = transactionManager.runInTransaction { clubRepository.getClubById(spaceId)?.logoUrl }
            GetPublicClubLogoUseCase.publicUrl(CoachConfig.publicBaseUrl, spaceId, logoUrl)
        }, opponentLogoUrl = { matchId ->
            val opponent = transactionManager.runInTransaction {
                matchRepository.getMatchById(matchId)?.let { opponentRepository.getOpponentById(it.opponentId) }
            }
            opponent?.let { publicImageLinks.opponentLogo(it.id, it.logoUrl) }
        })
    }
    factory<PushAudience> { CoachPushAudience(get(), get()) }
    factory { RegisterFcmTokenUseCase(get(), get()) }
    factory { RemoveFcmTokenUseCase(get(), get()) }
    // Images (TECHNICAL_DECISIONS §57, §72): its own bucket, keys under `coach/`, the disk locally,
    // none deployed without a bucket. The space's logo (a space is a club), opponents' logos, players'
    // photos and the account's own photo, through the shared use cases and imageRoutes.
    single<ImageStorage> {
        val bucket = CoachConfig.storageBucket
        PrefixedImageStorage(
            when {
                bucket != null -> S3ImageStorage(
                    bucket, CoachConfig.storageEndpoint, CoachConfig.storageRegion,
                    CoachConfig.storageAccessKeyId, CoachConfig.storageSecretAccessKey
                )
                !CoachConfig.deployed -> LocalImageStorage(File(CoachConfig.localImageDir))
                else -> UnavailableImageStorage
            },
            prefix = "coach/"
        )
    }
    factory { UploadClubLogoUseCase(get(), get(), get(), get()) }
    factory { DeleteClubLogoUseCase(get(), get(), get(), get()) }
    factory { GetClubLogoUseCase(get(), get(), get(), get()) }
    factory { UploadOpponentLogoUseCase(get(), get(), get(), get(), get()) }
    factory { DeleteOpponentLogoUseCase(get(), get(), get(), get(), get()) }
    factory { GetOpponentLogoUseCase(get(), get(), get(), get()) }
    factory { UploadAgeGroupLogoUseCase(get(), get(), get(), get()) }
    factory { DeleteAgeGroupLogoUseCase(get(), get(), get(), get()) }
    factory { GetAgeGroupLogoUseCase(get(), get(), get(), get()) }
    factory { DeleteAgeGroupUseCase(get(), get(), get(), get(), get()) }
    factory { DeleteOpponentUseCase(get(), get(), get(), get(), get(), get()) }
    factory { UploadPlayerPhotoUseCase(get(), get(), get(), get()) }
    factory { DeletePlayerPhotoUseCase(get(), get(), get(), get()) }
    factory { GetPlayerPhotoUseCase(get(), get(), get(), get()) }
    factory { UploadUserAvatarUseCase(get(), get(), get()) }
    factory { DeleteUserAvatarUseCase(get(), get(), get()) }
    factory { GetUserAvatarUseCase(get(), get(), get()) }
    // Pictures in pushes, served without signing in (publicImageRoutes): the space's and opponents'
    // current logos, and a scorer's photo behind a signed, expiring link.
    single { PublicImageLinks(CoachConfig.publicBaseUrl, CoachConfig.jwtSecret) }
    factory { GetPublicClubLogoUseCase(get(), get(), get()) }
    factory { GetPublicOpponentLogoUseCase(get(), get(), get()) }
    factory { GetPublicPlayerPhotoUseCase(get(), get(), get(), get()) }
    // No player or parent accounts: emails given with a player are refused.
    single<PlayerContacts> { PlayerContacts.None }

    // Accounts: the shared sign-up, sign-in and refresh. The space comes with the first setup.
    factory { RegisterUseCase(get(), get()) }
    factory { LoginUseCase(get(), get(), CoachTokens) }
    factory { RefreshTokenUseCase(get(), get(), CoachTokens) }

    // The account (accountRoutes, shared with the clubs server): profile, password, email change and
    // Forgot password, with the coach's own emails. The profile photo is in imageRoutes.
    single { CoachEmailService() }
    single<EmailService> { get<CoachEmailService>() }
    single<StaffInviteMailer> { get<CoachEmailService>() }
    single<PlanRequestMailer> { get<CoachEmailService>() }
    single<PasswordResetRepository> { ExposedPasswordResetRepository() }
    single<EmailChangeRepository> { ExposedEmailChangeRepository() }
    factory { CurrentPasswordGuard(get(), get()) }
    factory { GetCurrentUserUseCase(get(), get()) }
    factory { UpdateProfileUseCase(get(), get()) }
    factory { ChangePasswordUseCase(get(), get(), get()) }
    factory { RequestPasswordResetUseCase(get(), get(), get(), get()) }
    factory { ResetPasswordUseCase(get(), get(), get()) }
    factory { RequestEmailChangeUseCase(get(), get(), get(), get(), get()) }
    factory { ConfirmEmailChangeUseCase(get(), get(), get(), get()) }
    // Deleting the account, with the space they own (the stores require it).
    single<AccountDeletionRepository> { ExposedAccountDeletionRepository() }
    factory { GetAccountDeletionSummaryUseCase(get(), get()) }
    factory { DeleteAccountUseCase(get(), get(), get(), get()) }
    factory { DeleteSpaceUseCase(get(), get(), get(), get(), get()) }
    single<SpaceMembershipRepository> { ExposedSpaceMembershipRepository() }
    factory { LeaveSpaceUseCase(get(), get(), get()) }

    // Who may do what: the space's owner everywhere, its staff in their age groups.
    // Plans (REQUIREMENTS §5c): what each space's plan allows and locks, and the admin who sets them.
    single<SpacePlanRepository> { ExposedSpacePlanRepository() }
    factory { CoachPlans(get(), plansEnabled = CoachConfig.plansEnabled) }
    factory { PlatformAdmins(get(), CoachConfig.platformAdminEmails) }
    factory { GetAdminSpacesUseCase(get(), get(), get()) }
    factory { SetSpacePlanUseCase(get(), get(), get(), get()) }
    factory<AccessPolicy> { CoachAccessPolicy(get(), get(), get(), get(), get()) }
    factory { CoachSeasons(get()) }
    factory { SetUpSpaceUseCase(get(), get(), get(), get(), get()) }
    factory { GetSpacesUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetSpaceDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { AddAgeGroupsUseCase(get(), get(), get(), get(), get(), get()) }
    // How plans change: at once locally; deployed, in the store once it can check purchases, else by request.
    single<GooglePlayApi> { GooglePlayDeveloperApi(CoachConfig.googlePlayPackageName, CoachConfig.googlePlayServiceAccountJson) }
    single<StoreSubscriptionRepository> { ExposedStoreSubscriptionRepository() }
    factory {
        val changeBy = when {
            !CoachConfig.deployed -> PlanChange.INSTANT
            get<GooglePlayApi>().configured -> PlanChange.STORE
            else -> PlanChange.REQUEST
        }
        GetPlansUseCase(get(), get(), get(), changeBy, get())
    }
    factory { GoogleSubscriptions(get(), get(), get(), CoachConfig.acceptsTestPurchases, CoachConfig.acceptsRealPurchases) }
    factory { VerifyGooglePurchaseUseCase(get(), get(), get(), get(), get(), plansEnabled = CoachConfig.plansEnabled) }
    factory { HandleGoogleNotificationUseCase(get(), get(), get(), get(), get()) }
    // Locally a plan asked for is set at once; deployed, a platform admin sets it.
    factory { RequestPlanUseCase(get(), get(), get(), get(), CoachConfig.platformAdminEmails, atOnce = !CoachConfig.deployed, transactionManager = get(), plansEnabled = CoachConfig.plansEnabled) }

    // Staff: invite codes for an age group, and taking someone off one.
    single<StaffInviteRepository> { ExposedStaffInviteRepository() }
    factory { CreateStaffInviteUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetStaffInvitesUseCase(get(), get(), get(), get()) }
    factory { RevokeStaffInviteUseCase(get(), get(), get(), get()) }
    factory { AcceptStaffInviteUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { RemoveStaffUseCase(get(), get(), get(), get()) }

    // Age groups, teams, players and opponents: the shared use cases.
    factory { CreateTeamUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { CreateTeamsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetTeamPlayersUseCase(get(), get(), get(), get()) }
    factory { GetTeamDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { UpdateTeamUseCase(get(), get(), get(), get()) }
    factory { DeleteTeamUseCase(get(), get(), get(), get(), get()) }
    factory { GetAgeGroupsUseCase(get(), get(), get()) }
    factory { CreateAgeGroupsUseCase(get(), get(), get(), get()) }
    factory { UpdateAgeGroupUseCase(get(), get(), get(), get(), get()) }
    factory { GetAgeGroupDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetPlayerUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetPlayersUseCase(get(), get(), get(), get(), get()) }
    factory { GetAgeGroupPlayersByClubUseCase(get(), get(), get()) }
    factory { CreatePlayersUseCase(get(), get(), get(), get(), get()) }
    factory { UpdatePlayerUseCase(get(), get(), get(), get(), get()) }
    factory { AddPlayersToAgeGroupUseCase(get(), get(), get(), get()) }
    factory { RemovePlayerFromAgeGroupUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetEligiblePlayersForAgeGroupUseCase(get(), get(), get(), get()) }
    factory { GetTeamsUseCase(get(), get(), get()) }
    factory { CreateOpponentUseCase(get(), get(), get(), get()) }
    factory { GetOpponentsUseCase(get(), get(), get(), get()) }
    factory { GetOpponentListUseCase(get(), get(), get(), get(), get()) }
    factory { GetOpponentDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { UpdateOpponentUseCase(get(), get(), get(), get()) }

    // Matches, call-ups, the live game, the record and report, and stats: the shared use cases.
    factory { GetAgeGroupSeasonRecordUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetTeamSeasonRecordUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetFullStatsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetPlayerSeasonStatsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { CreateMatchUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { GetMatchesUseCase(get(), get(), get(), get(), get()) }
    factory { GetMatchDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { UpdateMatchUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { DeleteMatchUseCase(get(), get(), get(), get()) }
    factory { GetMatchReportUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { SaveMatchReportUseCase(get(), get(), get(), get(), get()) }
    factory { UpdateMatchStatusUseCase(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { StartMatchUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { SetMatchLineupUseCase(get(), get(), get(), get(), get(), get()) }
    factory { PauseMatchUseCase(get(), get(), get(), get(), get(), get()) }
    factory { TakeOverLiveMatchUseCase(get(), get(), get(), get(), get()) }
    factory { ResumeMatchUseCase(get(), get(), get(), get(), get(), get()) }
    factory { SetMatchMinuteUseCase(get(), get(), get(), get(), get(), get()) }
    factory { ManageCallUpsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { NotifyMatchEventUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { AddMatchEventUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { SaveMatchRecordUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { DeleteMatchEventUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetMatchEventsUseCase(get(), get(), get(), get()) }
    factory { GetMatchScoreUseCase(get(), get(), get(), get()) }
    factory { GetLiveMatchUpdatesUseCase(get(), get(), get(), get()) }
    factory { GetClubMatchesUpdatesUseCase(get(), get(), get()) }
}
